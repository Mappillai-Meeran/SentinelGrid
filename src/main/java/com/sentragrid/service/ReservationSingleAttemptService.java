package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.dto.MedicineDto;
import com.sentragrid.dto.PharmacyDto;
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
import com.sentragrid.manager.entity.ManagerSettings;
import com.sentragrid.manager.repository.ManagerSettingsRepository;
import com.sentragrid.repository.IdempotencyRecordRepository;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Helper service to execute a single reservation attempt inside a fresh REQUIRES_NEW transaction.
 * Ensures each optimistic-locking retry attempt starts with a discarded persistence context and fresh DB read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationSingleAttemptService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineRepository medicineRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final KafkaProducerService kafkaProducerService;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ManagerSettingsRepository managerSettingsRepository;

    /**
     * Executes a single reservation attempt inside a dedicated, isolated transaction (REQUIRES_NEW).
     * If optimistic locking occurs during save/commit, this transaction rolls back cleanly,
     * leaving zero partial state in the database.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReservationResponseDto executeSingleAttempt(Long patientId, ReservationRequestDto request, int defaultTtlMinutes, String idempotencyKey, String requestHash) {
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found with ID: " + patientId));

        Pharmacy pharmacy = pharmacyRepository.findById(request.getPharmacyId())
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with ID: " + request.getPharmacyId()));

        Medicine medicine = medicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found with ID: " + request.getMedicineId()));

        // Prescription requirements enforcement: persisted Medicine.requiresPrescription is the source of truth
        if (medicine.isRequiresPrescription()) {
            if (request.getPrescriptionNumber() == null || request.getPrescriptionNumber().trim().isEmpty() ||
                request.getDoctorName() == null || request.getDoctorName().trim().isEmpty() ||
                request.getDoctorRegNumber() == null || request.getDoctorRegNumber().trim().isEmpty()) {
                throw new BadRequestException("Prescription details (prescription number, prescribing doctor name, and doctor registration number) are required to reserve prescription-controlled medicine '" + medicine.getName() + "'");
            }
        }

        // Anti-hoarding check: Prevent multiple active reservations for the same patient and medicine category
        boolean hasActiveReservation = reservationRepository
                .existsActiveReservationForPatientAndCategory(patientId, medicine.getCategory());
        if (hasActiveReservation) {
            throw new BadRequestException("Patient already has an active PENDING reservation in medicine category: " + medicine.getCategory());
        }

        // Fetch fresh Inventory row from DB
        Inventory inventory = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy.getId(), medicine.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found for pharmacy ID: " + pharmacy.getId() + " and medicine ID: " + medicine.getId()));

        if (inventory.getAvailableQuantity() < request.getQuantity()) {
            throw new BadRequestException("Insufficient stock available. Available: " + inventory.getAvailableQuantity() + ", Requested: " + request.getQuantity());
        }

        // Branch Rule: Enforce branch-specific maximum emergency hold and hold expiry TTL if configured
        int effectiveTtlMinutes = defaultTtlMinutes;
        if (managerSettingsRepository != null) {
            java.util.Optional<ManagerSettings> settingsOpt = managerSettingsRepository.findById(pharmacy.getId());
            if (settingsOpt.isPresent()) {
                ManagerSettings settings = settingsOpt.get();
                if (settings.getMaxEmergencyHoldQuantity() != null && settings.getMaxEmergencyHoldQuantity() > 0) {
                    if (request.getQuantity() > settings.getMaxEmergencyHoldQuantity()) {
                        throw new BadRequestException("Requested quantity (" + request.getQuantity() +
                                ") exceeds branch maximum emergency hold limit of " + settings.getMaxEmergencyHoldQuantity() + " units for " + pharmacy.getName());
                    }
                }
                if (settings.getHoldExpiryMinutes() != null && settings.getHoldExpiryMinutes() > 0) {
                    effectiveTtlMinutes = settings.getHoldExpiryMinutes();
                }
            }
        }

        // Reserve stock
        inventory.setReservedQuantity(inventory.getReservedQuantity() + request.getQuantity());
        inventoryRepository.save(inventory);

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(effectiveTtlMinutes);

        Reservation reservation = Reservation.builder()
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(request.getQuantity())
                .status(ReservationStatus.PENDING)
                .expiresAt(expiresAt)
                .prescriptionNumber(medicine.isRequiresPrescription() ? request.getPrescriptionNumber().trim() : null)
                .doctorName(medicine.isRequiresPrescription() ? request.getDoctorName().trim() : null)
                .doctorRegNumber(medicine.isRequiresPrescription() ? request.getDoctorRegNumber().trim() : null)
                .prescriptionVerified(false)
                .prescriptionVerifiedBy(null)
                .prescriptionVerifiedAt(null)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);

        // Save Idempotency Record if key is present
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            com.sentragrid.entity.IdempotencyRecord idempotencyRecord = com.sentragrid.entity.IdempotencyRecord.builder()
                    .user(patient)
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .reservation(savedReservation)
                    .build();
            idempotencyRecordRepository.save(idempotencyRecord);
        }

        // Audit Trail
        auditLogService.logAction("Reservation", savedReservation.getId(), "RESERVATION_CREATED", patient.getUsername(),
                "Reserved quantity " + request.getQuantity() + " of " + medicine.getName() + " at pharmacy " + pharmacy.getName());

        // Publish Kafka Event
        ReservationCreatedEvent event = ReservationCreatedEvent.builder()
                .reservationId(savedReservation.getId())
                .patientId(patient.getId())
                .pharmacyId(pharmacy.getId())
                .medicineId(savedReservation.getMedicine().getId())
                .quantity(savedReservation.getQuantity())
                .expiresAt(expiresAt)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendReservationCreatedEvent(event);

        return mapToReservationResponseDto(savedReservation);
    }

    private ReservationResponseDto mapToReservationResponseDto(Reservation reservation) {
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

        Long remainingSeconds = null;
        Long remainingMinutes = null;
        Boolean expired = null;

        if (reservation.getExpiresAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(reservation.getExpiresAt())) {
                remainingSeconds = Duration.between(now, reservation.getExpiresAt()).getSeconds();
                remainingMinutes = remainingSeconds / 60;
                expired = false;
            } else {
                remainingSeconds = 0L;
                remainingMinutes = 0L;
                expired = true;
            }
        }

        return ReservationResponseDto.builder()
                .id(reservation.getId())
                .patientId(reservation.getPatient().getId())
                .patientUsername(reservation.getPatient().getUsername())
                .pharmacy(pharmacyDto)
                .medicine(medicineDto)
                .quantity(reservation.getQuantity())
                .status(reservation.getStatus())
                .expiresAt(reservation.getExpiresAt())
                .remainingSeconds(remainingSeconds)
                .remainingMinutes(remainingMinutes)
                .expired(expired)
                .confirmedAt(reservation.getConfirmedAt())
                .cancelledAt(reservation.getCancelledAt())
                .createdAt(reservation.getCreatedAt())
                .version(reservation.getVersion())
                .prescriptionNumber(reservation.getPrescriptionNumber())
                .doctorName(reservation.getDoctorName())
                .doctorRegNumber(reservation.getDoctorRegNumber())
                .prescriptionVerified(reservation.isPrescriptionVerified())
                .prescriptionVerifiedBy(reservation.getPrescriptionVerifiedBy())
                .prescriptionVerifiedAt(reservation.getPrescriptionVerifiedAt())
                .build();
    }
}
