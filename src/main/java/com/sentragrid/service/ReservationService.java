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
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.sentragrid.kafka.KafkaProducerService;
import com.sentragrid.kafka.event.ReservationConfirmedEvent;
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

import java.time.Duration;
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
    private final ReservationSingleAttemptService singleAttemptService;
    private final com.sentragrid.repository.IdempotencyRecordRepository idempotencyRecordRepository;

    @Value("${app.reservation.default-ttl-minutes:30}")
    private int defaultTtlMinutes;

    /**
     * Create a reservation for a patient with Optimistic Locking, bounded retries, and request idempotency.
     * Each attempt executes in an isolated fresh REQUIRES_NEW transaction.
     * Prevents multiple active reservations for the same patient and medicine category.
     */
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public ReservationResponseDto createReservation(Long patientId, ReservationRequestDto request) {
        return createReservation(patientId, request, null);
    }

    /**
     * Create a reservation with optional Idempotency-Key.
     */
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public ReservationResponseDto createReservation(Long patientId, ReservationRequestDto request, String idempotencyKey) {
        String cleanKey = null;
        String requestHash = null;

        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            cleanKey = idempotencyKey.trim();
            if (cleanKey.length() > 255) {
                throw new BadRequestException("Idempotency key exceeds maximum length of 255 characters");
            }

            requestHash = computeRequestHash(request);

            // Check if key was already processed for this patient
            var existingRecord = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(patientId, cleanKey);
            if (existingRecord.isPresent()) {
                var record = existingRecord.get();
                if (!record.getRequestHash().equals(requestHash)) {
                    throw new BadRequestException("Idempotency key was previously used with a different reservation request");
                }
                log.info("Returning existing idempotent reservation ID: {} for key: {} and patient ID: {}",
                        record.getReservation().getId(), cleanKey, patientId);
                return mapToReservationResponseDto(record.getReservation());
            }
        }

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return singleAttemptService.executeSingleAttempt(patientId, request, defaultTtlMinutes, cleanKey, requestHash);
            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException | org.hibernate.StaleObjectStateException ex) {
                log.warn("Optimistic lock conflict on reservation attempt {}/{} for patient ID: {}, inventory update conflict: {}",
                        attempt, maxAttempts, patientId, ex.getMessage());
                if (attempt == maxAttempts) {
                    throw ex;
                }
                backoffDelay(attempt);
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // If a concurrent request inserted the exact same idempotency key, re-query the existing record after transaction rollback
                if (cleanKey != null) {
                    for (int lookupAttempt = 0; lookupAttempt < 5; lookupAttempt++) {
                        var existingRecord = idempotencyRecordRepository.findByUserIdAndIdempotencyKey(patientId, cleanKey);
                        if (existingRecord.isPresent()) {
                            var record = existingRecord.get();
                            if (!record.getRequestHash().equals(requestHash)) {
                                throw new BadRequestException("Idempotency key was previously used with a different reservation request");
                            }
                            log.info("Concurrent request resolved existing idempotent reservation ID: {} for key: {} and patient ID: {}",
                                    record.getReservation().getId(), cleanKey, patientId);
                            return mapToReservationResponseDto(record.getReservation());
                        }
                        try {
                            Thread.sleep(30);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                throw ex;
            }
        }
        throw new ObjectOptimisticLockingFailureException(Inventory.class, request.getMedicineId());
    }

    private String computeRequestHash(ReservationRequestDto request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getPharmacyId()).append(":").append(request.getMedicineId()).append(":").append(request.getQuantity());
        if (request.getPrescriptionNumber() != null && !request.getPrescriptionNumber().trim().isEmpty()) {
            sb.append(":").append(request.getPrescriptionNumber().trim());
        }
        if (request.getDoctorName() != null && !request.getDoctorName().trim().isEmpty()) {
            sb.append(":").append(request.getDoctorName().trim());
        }
        if (request.getDoctorRegNumber() != null && !request.getDoctorRegNumber().trim().isEmpty()) {
            sb.append(":").append(request.getDoctorRegNumber().trim());
        }
        String raw = sb.toString();
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return String.valueOf(raw.hashCode());
        }
    }

    private void backoffDelay(int attempt) {
        try {
            long baseDelayMs = 30L * attempt;
            long jitter = (long) (Math.random() * 20L);
            Thread.sleep(baseDelayMs + jitter);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Cancel an existing PENDING reservation and release reserved stock.
     */
    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public ReservationResponseDto cancelReservation(Long reservationId, String username) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));

        validateReservationAccess(reservation, username, "cancel");

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

        validatePharmacistPharmacyScope(reservation, confirmedBy);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Only PENDING reservations can be confirmed. Current status: " + reservation.getStatus());
        }

        if (reservation.getExpiresAt() != null && LocalDateTime.now().isAfter(reservation.getExpiresAt())) {
            throw new BadRequestException("Cannot confirm pickup: Reservation has expired");
        }

        // Prescription verification gate: For prescription-required medicines, verification must be completed first
        if (reservation.getMedicine().isRequiresPrescription() && !reservation.isPrescriptionVerified()) {
            throw new BadRequestException("Cannot confirm pickup: Prescription verification is pending. An authorized pharmacist must verify the prescription before pickup confirmation.");
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

        // Publish Kafka Event
        ReservationConfirmedEvent event = ReservationConfirmedEvent.builder()
                .reservationId(updatedReservation.getId())
                .patientId(updatedReservation.getPatient().getId())
                .pharmacyId(updatedReservation.getPharmacy().getId())
                .medicineId(updatedReservation.getMedicine().getId())
                .quantity(updatedReservation.getQuantity())
                .confirmedBy(confirmedBy)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendReservationConfirmedEvent(event);

        return mapToReservationResponseDto(updatedReservation, confirmedBy);
    }

    /**
     * Verify prescription for a reservation.
     * Only an authenticated pharmacist/manager assigned to the reservation's pharmacy can verify.
     */
    @Transactional
    public ReservationResponseDto verifyPrescription(Long reservationId, String verifiedBy) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + reservationId));

        validatePharmacistPharmacyScope(reservation, verifiedBy);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Only PENDING reservations can have prescriptions verified. Current status: " + reservation.getStatus());
        }

        if (reservation.getExpiresAt() != null && LocalDateTime.now().isAfter(reservation.getExpiresAt())) {
            throw new BadRequestException("Cannot verify prescription: Reservation has expired");
        }

        if (!reservation.getMedicine().isRequiresPrescription()) {
            throw new BadRequestException("Medicine '" + reservation.getMedicine().getName() + "' does not require a prescription");
        }

        reservation.setPrescriptionVerified(true);
        reservation.setPrescriptionVerifiedBy(verifiedBy);
        reservation.setPrescriptionVerifiedAt(LocalDateTime.now());

        Reservation updatedReservation = reservationRepository.save(reservation);

        auditLogService.logAction("Reservation", reservationId, "PRESCRIPTION_VERIFIED", verifiedBy,
                "Pharmacist " + verifiedBy + " verified prescription #" + reservation.getPrescriptionNumber() +
                " for reservation ID " + reservationId);

        return mapToReservationResponseDto(updatedReservation, verifiedBy);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getPatientReservations(Long patientId) {
        return getPatientReservations(patientId, null);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getPatientReservations(Long patientId, ReservationStatus status) {
        List<Reservation> reservations;
        if (status == null) {
            reservations = reservationRepository.findByPatientId(patientId);
        } else {
            reservations = reservationRepository.findByPatientIdAndStatus(patientId, status);
        }
        return reservations.stream()
                .map(this::mapToReservationResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all reservations for a specific pharmacy branch.
     * Enforces strict branch-scoping: Pharmacist/Manager must be assigned to targetPharmacyId.
     */
    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getPharmacyReservations(Long pharmacyId, ReservationStatus status, String username) {
        verifyPharmacyScopeForStaff(username, pharmacyId);

        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId);
        }

        List<Reservation> reservations;
        if (status == null) {
            reservations = reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacyId);
        } else {
            reservations = reservationRepository.findByPharmacyIdAndStatusOrderByCreatedAtDesc(pharmacyId, status);
        }

        return reservations.stream()
                .map(r -> mapToReservationResponseDto(r, username))
                .collect(Collectors.toList());
    }

    private void verifyPharmacyScopeForStaff(String username, Long targetPharmacyId) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Access denied: Authentication required to view pharmacy reservations.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Access denied: User account '" + username + "' not found."));

        if (user.getRole() == UserRole.ADMIN) {
            return;
        }

        if (user.getRole() == UserRole.PHARMACIST || user.getRole() == UserRole.PHARMACY_MANAGER) {
            if (user.getPharmacy() == null) {
                throw new AccessDeniedException("Access denied: No pharmacy assigned to account '" + username + "'.");
            }
            if (!user.getPharmacy().getId().equals(targetPharmacyId)) {
                throw new AccessDeniedException("Access denied: You are authorized only for pharmacy ID " +
                        user.getPharmacy().getId() + ", but attempted access to pharmacy ID " + targetPharmacyId);
            }
            return;
        }

        throw new AccessDeniedException("Access denied: Role '" + user.getRole() + "' is not authorized to view pharmacy reservations.");
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservationsForUser(String username) {
        return getReservationsForUser(username, null);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDto> getReservationsForUser(String username, ReservationStatus status) {
        List<Reservation> reservations;
        if (status == null) {
            reservations = reservationRepository.findByPatientUsernameOrderByCreatedAtDesc(username);
        } else {
            reservations = reservationRepository.findByPatientUsernameAndStatusOrderByCreatedAtDesc(username, status);
        }
        return reservations.stream()
                .map(this::mapToReservationResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationResponseDto getReservationById(Long id, String username) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with ID: " + id));

        if (username != null) {
            validateReservationAccess(reservation, username, "access");
        }

        return mapToReservationResponseDto(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponseDto getReservationById(Long id) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
        return getReservationById(id, username);
    }

    private void validateReservationAccess(Reservation reservation, String username, String action) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Access denied: Authentication required to " + action + " reservation");
        }
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Access denied: User account '" + username + "' not found"));

        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        if (currentUser.getRole() == UserRole.PATIENT) {
            if (!reservation.getPatient().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Access denied: You do not have permission to " + action + " another patient's reservation");
            }
            return;
        }

        if (currentUser.getRole() == UserRole.PHARMACIST || currentUser.getRole() == UserRole.PHARMACY_MANAGER) {
            if (currentUser.getPharmacy() == null) {
                throw new AccessDeniedException("Access denied: No pharmacy assigned to account '" + username + "'");
            }
            if (!reservation.getPharmacy().getId().equals(currentUser.getPharmacy().getId())) {
                throw new AccessDeniedException("Access denied: You are not authorized for reservations at pharmacy ID " + reservation.getPharmacy().getId());
            }
            return;
        }

        throw new AccessDeniedException("Access denied: Role '" + currentUser.getRole() + "' is not authorized to " + action + " reservations");
    }

    private void validatePharmacistPharmacyScope(Reservation reservation, String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Access denied: Authentication required to confirm pickup");
        }
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Access denied: User account '" + username + "' not found"));

        if (currentUser.getRole() == UserRole.ADMIN) {
            return;
        }

        if (currentUser.getRole() == UserRole.PATIENT) {
            throw new AccessDeniedException("Access denied: Patients cannot confirm pickup");
        }

        if (currentUser.getRole() == UserRole.PHARMACIST || currentUser.getRole() == UserRole.PHARMACY_MANAGER) {
            if (currentUser.getPharmacy() == null) {
                throw new AccessDeniedException("Access denied: No pharmacy assigned to pharmacist '" + username + "'");
            }
            if (!reservation.getPharmacy().getId().equals(currentUser.getPharmacy().getId())) {
                throw new AccessDeniedException("Access denied: Pharmacist is not authorized for pharmacy ID " + reservation.getPharmacy().getId());
            }
            return;
        }

        throw new AccessDeniedException("Access denied: Role '" + currentUser.getRole() + "' is not authorized to confirm pickup");
    }

    public ReservationResponseDto mapToReservationResponseDto(Reservation reservation) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
        return mapToReservationResponseDto(reservation, username);
    }

    public ReservationResponseDto mapToReservationResponseDto(Reservation reservation, String viewingUsername) {
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

        // Privacy rule: Only the reservation-owning patient, assigned pharmacy staff, or admin can view prescription metadata
        boolean canViewPrescriptionDetails = false;
        if (viewingUsername != null && !viewingUsername.isBlank()) {
            User viewer = userRepository.findByUsername(viewingUsername).orElse(null);
            if (viewer != null) {
                if (viewer.getRole() == UserRole.ADMIN) {
                    canViewPrescriptionDetails = true;
                } else if (viewer.getRole() == UserRole.PATIENT && reservation.getPatient() != null && reservation.getPatient().getId().equals(viewer.getId())) {
                    canViewPrescriptionDetails = true;
                } else if ((viewer.getRole() == UserRole.PHARMACIST || viewer.getRole() == UserRole.PHARMACY_MANAGER)
                        && viewer.getPharmacy() != null && reservation.getPharmacy() != null && reservation.getPharmacy().getId().equals(viewer.getPharmacy().getId())) {
                    canViewPrescriptionDetails = true;
                }
            }
        }

        return ReservationResponseDto.builder()
                .id(reservation.getId())
                .patientId(reservation.getPatient() != null ? reservation.getPatient().getId() : null)
                .patientUsername(reservation.getPatient() != null ? reservation.getPatient().getUsername() : null)
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
                .prescriptionNumber(canViewPrescriptionDetails ? reservation.getPrescriptionNumber() : null)
                .doctorName(canViewPrescriptionDetails ? reservation.getDoctorName() : null)
                .doctorRegNumber(canViewPrescriptionDetails ? reservation.getDoctorRegNumber() : null)
                .prescriptionVerified(reservation.isPrescriptionVerified())
                .prescriptionVerifiedBy(canViewPrescriptionDetails ? reservation.getPrescriptionVerifiedBy() : null)
                .prescriptionVerifiedAt(canViewPrescriptionDetails ? reservation.getPrescriptionVerifiedAt() : null)
                .build();
    }
}
