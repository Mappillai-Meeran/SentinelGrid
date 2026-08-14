package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.dto.PharmacyDto;
import com.sentragrid.dto.MedicineDto;
import com.sentragrid.dto.ReservationRequestDto;
import com.sentragrid.dto.ReservationResponseDto;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.exception.ResourceNotFoundException;
import com.sentragrid.kafka.KafkaProducerService;
import com.sentragrid.kafka.event.ReservationCreatedEvent;
import com.sentragrid.kafka.event.ReservationExpiredEvent;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing reservation lifecycle.
 * Protects against double-booking using JPA Optimistic Locking (@Version) on Inventory and Reservation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineRepository medicineRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final KafkaProducerService kafkaProducerService;

    @Value("${app.reservation.default-ttl-minutes:30}")
    private int defaultTtlMinutes;

    /**
     * Create a reservation for a patient with Optimistic Locking.
     * Prevents multiple active reservations for the same patient and medicine category.
     */
    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public ReservationResponseDto createReservation(Long patientId, ReservationRequestDto request) {
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with ID: " + patientId));

        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with ID: " + request.getPharmacyId()));

        Medicine medicine = medicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found with ID: " + request.getMedicineId()));

        // Rule: Prevent multiple active reservations for the same patient and medicine category
        boolean hasActiveReservation = reservationRepository
                .existsActiveReservationForPatientAndCategory(patientId, medicine.getCategory());
        if (hasActiveReservation) {
            throw new BadRequestException("Patient already has an active PENDING reservation in medicine category: " + medicine.getCategory());
        }

        // Fetch Inventory (Optimistic Locking via JPA @Version)
        Inventory inventory = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy.getId(), medicine.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for pharmacy ID: " + pharmacy.getId() + " and medicine ID: " + medicine.getId()));

        if (inventory.getAvailableQuantity() < request.getQuantity()) {
            throw new BadRequestException("Insufficient stock available. Available: " + inventory.getAvailableQuantity() + ", Requested: " + request.getQuantity());
        }

        // Reserve stock
        inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
        try {
            inventoryRepository.save(inventory);
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.error("Optimistic lock conflict when reserving stock for inventory ID: {}", inventory.getId());
            throw ex;
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(defaultTtlMinutes);

        Reservation reservation = Reservation.builder()
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(request.getQuantity())
                .status(ReservationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);

        // Audit Trail
        auditLogService.logAction("Reservation", savedReservation.getId(), "RESERVATION_CREATED", patient.getUsername(),
                "Reserved quantity " + request.getQuantity() + " of " + medicine.getName() + " at pharmacy " + pharmacy.getName());

        // Publish Kafka Event
        ReservationCreatedEvent event = ReservationCreatedEvent.builder()
                .reservationId(savedReservation.getId())
                .patientId(patient.getId())
                .pharmacyId(pharmacy.getId())
                .medicineId(medicine.getId())
                .quantity(savedReservation.getQuantity())
                .expiresAt(expiresAt)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendReservationCreatedEvent(event);

        return mapToReservationResponseDto(savedReservation);
    }

    /**
     * Cancel an existing PENDING reservation and release reserved stock.
     */
    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public ReservationResponseDto cancelReservation(Long reservationId, String username) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Only PENDING reservations can be cancelled. Current status: " + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(LocalDateTime.now());

        // Release reserved quantity
        Inventory inventory = inventoryRepository.findByPharmacyIdAndMedicineId(
                reservation.getPharmacy().getId(), reservation.getMedicine().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found"));

        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - reservation.getQuantity()));
        inventoryRepository.save(inventory);

        Reservation updatedReservation = reservationRepository.save(reservation);

        auditLogService.logAction("Reservation", reservationId, "RESERVATION_CANCELLED", username,
                "Cancelled reservation and released " + reservation.getQuantity() + " reserved units");

        return mapToReservationResponseDto(updatedReservation);
    }

    /**
     * Confirm pickup of reserved medicine. Converts reserved stock into permanent deduction.
     */
    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public ReservationResponseDto confirmPickup(Long reservationId, String confirmedBy) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Only PENDING reservations can be confirmed. Current status: " + reservation.getStatus());
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setConfirmedAt(LocalDateTime.now());

        // Permanently deduct quantity and release reserved quantity
        Inventory inventory = inventoryRepository.findByPharmacyIdAndMedicineId(
                reservation.getPharmacy().getId(), reservation.getMedicine().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found"));

        inventory.setQuantity(Math.max(0, inventory.getQuantity() - reservation.getQuantity()));
        inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - reservation.getQuantity()));
        inventoryRepository.save(inventory);

        Reservation updatedReservation = reservationRepository.save(reservation);

        auditLogService.logAction("Reservation", reservationId, "RESERVATION_CONFIRMED", confirmedBy,
                "Confirmed pickup of " + reservation.getQuantity() + " units of " + reservation.getMedicine().getName());

        return mapToReservationResponseDto(updatedReservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getPatientReservations(Long patientId) {
        return reservationRepository.findByPatientId(patientId).stream()
                .map(this::mapToReservationResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationResponseDto getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + id));
        return mapToReservationResponseDto(reservation);
    }

    public ReservationResponseDto mapToReservationResponseDto(Reservation reservation) {
        PharmacyDto pharmacyDto = PharmacyDto.builder()
                .id(reservation.getPharmacy().getId())
                .name(reservation.getPharmacy().getName())
                .address(reservation.getPharmacy().getAddress())
                .city(reservation.getPharmacy().getCity())
                .latitude(reservation.getPharmacy().getLatitude())
                .longitude(reservation.getPharmacy().getLongitude())
                .contactNumber(reservation.getPharmacy().getContactNumber())
                .build();

        MedicineDto medicineDto = MedicineDto.builder()
                .id(reservation.getMedicine().getId())
                .name(reservation.getMedicine().getName())
                .brand(reservation.getMedicine().getBrand())
                .category(reservation.getMedicine().getCategory())
                .description(reservation.getMedicine().getDescription())
                .dosage(reservation.getMedicine().getDosage())
                .requiresPrescription(reservation.getMedicine().isRequiresPrescription())
                .build();

        return ReservationResponseDto.builder()
                .id(reservation.getId())
                .patientId(reservation.getPatient().getId())
                .patientUsername(reservation.getPatient().getUsername())
                .pharmacy(pharmacyDto)
                .medicine(medicineDto)
                .quantity(reservation.getQuantity())
                .status(reservation.getStatus())
                .expiresAt(reservation.getExpiresAt())
                .confirmedAt(reservation.getConfirmedAt())
                .cancelledAt(reservation.getCancelledAt())
                .createdAt(reservation.getCreatedAt())
                .version(reservation.getVersion())
                .build();
    }
}
