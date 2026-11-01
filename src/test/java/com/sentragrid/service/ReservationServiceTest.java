package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
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
import com.sentragrid.kafka.KafkaProducerService;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private ReservationSingleAttemptService singleAttemptService;

    @Mock
    private com.sentragrid.repository.IdempotencyRecordRepository idempotencyRecordRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User patient;
    private Pharmacy pharmacy;
    private Medicine medicine;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "defaultTtlMinutes", 30);

        patient = User.builder().id(1L).username("john_doe").role(UserRole.PATIENT).build();
        pharmacy = Pharmacy.builder().id(10L).name("Apollo").city("Bangalore").contactNumber("1234").build();
        medicine = Medicine.builder().id(100L).name("Remdesivir").category("ANTIVIRAL").requiresPrescription(true).build();
        inventory = Inventory.builder().id(1000L).pharmacy(pharmacy).medicine(medicine).quantity(10).reservedQuantity(0).version(0L).build();
    }

    @Test
    @DisplayName("Create Reservation - Success on First Attempt")
    void testCreateReservation_Success() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .build();

        ReservationResponseDto expectedResponse = ReservationResponseDto.builder()
                .id(500L)
                .status(ReservationStatus.PENDING)
                .quantity(2)
                .build();

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any())).thenReturn(expectedResponse);

        ReservationResponseDto response = reservationService.createReservation(1L, request);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(ReservationStatus.PENDING, response.getStatus());
        verify(singleAttemptService, times(1)).executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any());
    }

    @Test
    @DisplayName("Create Reservation - Optimistic Lock Conflict Succeeds on Second Attempt (TEST 1)")
    void testCreateReservation_RetrySucceedsOnSecondAttempt() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .build();

        ReservationResponseDto expectedResponse = ReservationResponseDto.builder()
                .id(500L)
                .status(ReservationStatus.PENDING)
                .quantity(2)
                .build();

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Inventory.class, 1000L))
                .thenReturn(expectedResponse);

        ReservationResponseDto response = reservationService.createReservation(1L, request);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        verify(singleAttemptService, times(2)).executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any());
    }

    @Test
    @DisplayName("Create Reservation - Optimistic Lock Conflict Succeeds on Third Attempt (TEST 2)")
    void testCreateReservation_RetrySucceedsOnThirdAttempt() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .build();

        ReservationResponseDto expectedResponse = ReservationResponseDto.builder()
                .id(500L)
                .status(ReservationStatus.PENDING)
                .quantity(2)
                .build();

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Inventory.class, 1000L))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Inventory.class, 1000L))
                .thenReturn(expectedResponse);

        ReservationResponseDto response = reservationService.createReservation(1L, request);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        verify(singleAttemptService, times(3)).executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any());
    }

    @Test
    @DisplayName("Create Reservation - All 3 Attempts Fail Throws OptimisticLockingFailureException (TEST 3)")
    void testCreateReservation_AllAttemptsFail() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .build();

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any()))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Inventory.class, 1000L));

        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> reservationService.createReservation(1L, request));

        verify(singleAttemptService, times(3)).executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any());
    }

    @Test
    @DisplayName("Create Reservation - Insufficient Stock is NOT Retried (TEST 4)")
    void testCreateReservation_InsufficientStock_NotRetried() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(15)
                .build();

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any()))
                .thenThrow(new BadRequestException("Insufficient stock available"));

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(1L, request));

        verify(singleAttemptService, times(1)).executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any());
    }

    @Test
    @DisplayName("Create Reservation - Business Validation Failure is NOT Retried (TEST 5)")
    void testCreateReservation_BusinessValidation_NotRetried() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .build();

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any()))
                .thenThrow(new BadRequestException("Patient already has an active PENDING reservation"));

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(1L, request));

        verify(singleAttemptService, times(1)).executeSingleAttempt(eq(1L), eq(request), eq(30), any(), any());
    }

    @Test
    @DisplayName("Cancel Reservation - Success")
    void testCancelReservation_Success() {
        Reservation reservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .prescriptionVerified(true)
                .prescriptionVerifiedBy("pharmacist1")
                .prescriptionVerifiedAt(LocalDateTime.now())
                .build();

        inventory.setReservedQuantity(2);

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(patient));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        ReservationResponseDto response = reservationService.cancelReservation(500L, "john_doe");

        assertEquals(ReservationStatus.CANCELLED, response.getStatus());
        assertEquals(0, inventory.getReservedQuantity());
        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("RESERVATION_CANCELLED"), eq("john_doe"), anyString());
    }

    @Test
    @DisplayName("Confirm Pickup - Success for Authorized Pharmacist")
    void testConfirmPickup_Success() {
        Reservation reservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .prescriptionVerified(true)
                .prescriptionVerifiedBy("pharmacist1")
                .prescriptionVerifiedAt(LocalDateTime.now())
                .build();

        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();

        inventory.setQuantity(10);
        inventory.setReservedQuantity(2);

        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        ReservationResponseDto response = reservationService.confirmPickup(500L, "pharmacist1");

        assertEquals(ReservationStatus.CONFIRMED, response.getStatus());
        assertEquals(8, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("RESERVATION_CONFIRMED"), eq("pharmacist1"), anyString());
    }

    @Test
    @DisplayName("Security - Patient can access own reservation")
    void testPatientCanAccessOwnReservation() {
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).build();
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(patient));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        ReservationResponseDto response = reservationService.getReservationById(500L, "john_doe");
        assertNotNull(response);
        assertEquals(500L, response.getId());
    }

    @Test
    @DisplayName("Security - Patient cannot access another patient's reservation")
    void testPatientCannotAccessOtherPatientReservation() {
        User otherPatient = User.builder().id(2L).username("other_patient").role(UserRole.PATIENT).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).build();

        when(userRepository.findByUsername("other_patient")).thenReturn(Optional.of(otherPatient));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.getReservationById(500L, "other_patient"));
    }

    @Test
    @DisplayName("Security - Patient cannot cancel another patient's reservation")
    void testPatientCannotCancelOtherPatientReservation() {
        User otherPatient = User.builder().id(2L).username("other_patient").role(UserRole.PATIENT).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).status(ReservationStatus.PENDING).build();

        when(userRepository.findByUsername("other_patient")).thenReturn(Optional.of(otherPatient));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.cancelReservation(500L, "other_patient"));
    }

    @Test
    @DisplayName("Security - Pharmacist cannot confirm reservation for another pharmacy")
    void testPharmacistCannotConfirmOtherPharmacy() {
        Pharmacy otherPharmacy = Pharmacy.builder().id(99L).name("Other Pharmacy").build();
        User pharmacistOtherPharmacy = User.builder().id(2L).username("pharmacist2").role(UserRole.PHARMACIST).pharmacy(otherPharmacy).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).status(ReservationStatus.PENDING).build();

        when(userRepository.findByUsername("pharmacist2")).thenReturn(Optional.of(pharmacistOtherPharmacy));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.confirmPickup(500L, "pharmacist2"));
    }

    @Test
    @DisplayName("Security - Admin retains access to view and confirm any reservation")
    void testAdminCanConfirmAnyReservation() {
        User admin = User.builder().id(3L).username("admin1").role(UserRole.ADMIN).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).quantity(2).status(ReservationStatus.PENDING).prescriptionVerified(true).build();

        inventory.setQuantity(10);
        inventory.setReservedQuantity(2);

        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        ReservationResponseDto response = reservationService.confirmPickup(500L, "admin1");
        assertNotNull(response);
        assertEquals(ReservationStatus.CONFIRMED, response.getStatus());
    }

    @Test
    @DisplayName("Security - Unassigned pharmacist cannot cancel reservation")
    void testUnassignedPharmacistCannotCancelReservation() {
        User unassignedPharmacist = User.builder().id(4L).username("unassigned_ph").role(UserRole.PHARMACIST).pharmacy(null).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).status(ReservationStatus.PENDING).build();

        when(userRepository.findByUsername("unassigned_ph")).thenReturn(Optional.of(unassignedPharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.cancelReservation(500L, "unassigned_ph"));
    }

    @Test
    @DisplayName("Security - Pharmacist cannot cancel reservation for another pharmacy")
    void testPharmacistCannotCancelOtherPharmacyReservation() {
        Pharmacy otherPharmacy = Pharmacy.builder().id(99L).name("Other Pharmacy").build();
        User pharmacistOtherPharmacy = User.builder().id(5L).username("pharmacist_other").role(UserRole.PHARMACIST).pharmacy(otherPharmacy).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).status(ReservationStatus.PENDING).build();

        when(userRepository.findByUsername("pharmacist_other")).thenReturn(Optional.of(pharmacistOtherPharmacy));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.cancelReservation(500L, "pharmacist_other"));
    }

    @Test
    @DisplayName("Security - Authorized pharmacist can cancel reservation belonging to assigned pharmacy")
    void testAuthorizedPharmacistCanCancelAssignedPharmacyReservation() {
        User assignedPharmacist = User.builder().id(6L).username("assigned_ph").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();
        Reservation reservation = Reservation.builder().id(500L).patient(patient).pharmacy(pharmacy).medicine(medicine).quantity(2).status(ReservationStatus.PENDING).prescriptionVerified(true).build();

        inventory.setReservedQuantity(2);

        when(userRepository.findByUsername("assigned_ph")).thenReturn(Optional.of(assignedPharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        ReservationResponseDto response = reservationService.cancelReservation(500L, "assigned_ph");
        assertNotNull(response);
        assertEquals(ReservationStatus.CANCELLED, response.getStatus());
    }

    // ==========================================
    // STEP 4 - PRESCRIPTION WORKFLOW TESTS
    // ==========================================

    @Test
    @DisplayName("Step 4 - Pickup confirmation blocked while prescription verification is pending")
    void testConfirmPickup_BlockedWhenPrescriptionPending() {
        Reservation unverifiedReservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .prescriptionVerified(false)
                .build();

        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();

        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(unverifiedReservation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.confirmPickup(500L, "pharmacist1"));
        assertTrue(ex.getMessage().contains("Prescription verification is pending"),
                "Must block pickup confirmation until prescription is verified");
    }

    @Test
    @DisplayName("Step 4 - Authorized pharmacist verifies prescription and records audit fields")
    void testVerifyPrescription_Success() {
        Reservation unverifiedReservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .prescriptionNumber("RX-12345")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-98765")
                .prescriptionVerified(false)
                .build();

        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();

        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(unverifiedReservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponseDto response = reservationService.verifyPrescription(500L, "pharmacist1");

        assertNotNull(response);
        assertTrue(response.isPrescriptionVerified(), "Prescription must now be verified");
        assertEquals("pharmacist1", response.getPrescriptionVerifiedBy());
        assertNotNull(response.getPrescriptionVerifiedAt());
        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("PRESCRIPTION_VERIFIED"), eq("pharmacist1"), anyString());
    }

    @Test
    @DisplayName("Step 4 - Pharmacist from wrong branch cannot verify prescription")
    void testVerifyPrescription_WrongBranchRejected() {
        Pharmacy otherPharmacy = Pharmacy.builder().id(99L).name("Other Branch").city("Chennai").build();
        User wrongBranchPharmacist = User.builder().id(5L).username("pharmacist_wrong").role(UserRole.PHARMACIST).pharmacy(otherPharmacy).build();

        Reservation unverifiedReservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .prescriptionVerified(false)
                .build();

        when(userRepository.findByUsername("pharmacist_wrong")).thenReturn(Optional.of(wrongBranchPharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(unverifiedReservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.verifyPrescription(500L, "pharmacist_wrong"));
    }

    @Test
    @DisplayName("Step 4 - Patient cannot verify prescription")
    void testVerifyPrescription_PatientRejected() {
        Reservation unverifiedReservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .prescriptionVerified(false)
                .build();

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(patient));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(unverifiedReservation));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> reservationService.verifyPrescription(500L, "john_doe"));
    }

    @Test
    @DisplayName("Step 4 - Non-prescription medicine confirms pickup directly without prescription verification")
    void testConfirmPickup_NonPrescriptionMedicineSucceedsWithoutVerification() {
        Medicine otcMed = Medicine.builder().id(101L).name("Oxygen").category("RESPIRATORY").requiresPrescription(false).build();
        Reservation otcReservation = Reservation.builder()
                .id(600L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(otcMed)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .prescriptionVerified(false)
                .build();

        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();
        Inventory otcInv = Inventory.builder().id(1001L).pharmacy(pharmacy).medicine(otcMed).quantity(5).reservedQuantity(1).version(0L).build();

        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(reservationRepository.findById(600L)).thenReturn(Optional.of(otcReservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 101L)).thenReturn(Optional.of(otcInv));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(otcReservation);

        ReservationResponseDto response = reservationService.confirmPickup(600L, "pharmacist1");

        assertNotNull(response);
        assertEquals(ReservationStatus.CONFIRMED, response.getStatus());
        assertEquals(4, otcInv.getQuantity());
        assertEquals(0, otcInv.getReservedQuantity());
    }

    @Test
    @DisplayName("Step 4 - Sensitive prescription metadata is masked for unauthorized users")
    void testPrescriptionMetadataMaskedForUnauthorizedUsers() {
        Reservation rxReservation = Reservation.builder()
                .id(700L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .prescriptionNumber("CONFIDENTIAL-RX-999")
                .doctorName("Dr. Secret")
                .doctorRegNumber("REG-SECRET-111")
                .prescriptionVerified(true)
                .prescriptionVerifiedBy("pharmacist1")
                .prescriptionVerifiedAt(LocalDateTime.now())
                .build();

        // 1. Another patient (unauthorized)
        User unauthorizedPatient = User.builder().id(99L).username("unauthorized_user").role(UserRole.PATIENT).build();
        when(userRepository.findByUsername("unauthorized_user")).thenReturn(Optional.of(unauthorizedPatient));

        ReservationResponseDto maskedDto = reservationService.mapToReservationResponseDto(rxReservation, "unauthorized_user");
        assertNotNull(maskedDto);
        assertNull(maskedDto.getPrescriptionNumber(), "Prescription number must be masked for unauthorized users");
        assertNull(maskedDto.getDoctorName(), "Doctor name must be masked");
        assertNull(maskedDto.getDoctorRegNumber(), "Doctor registration number must be masked");
        assertNull(maskedDto.getPrescriptionVerifiedBy(), "Verifier identity must be masked");
        assertTrue(maskedDto.isPrescriptionVerified(), "Verification flag can remain visible");

        // 2. Reservation-owning patient (authorized)
        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(patient));
        ReservationResponseDto ownerDto = reservationService.mapToReservationResponseDto(rxReservation, "john_doe");
        assertEquals("CONFIDENTIAL-RX-999", ownerDto.getPrescriptionNumber(), "Owner can view prescription number");
        assertEquals("Dr. Secret", ownerDto.getDoctorName());
    }

    @Test
    @DisplayName("Step 4 - Idempotency key with conflicting prescription payload throws BadRequestException")
    void testIdempotency_ConflictingPrescriptionPayloadThrowsBadRequest() {
        ReservationRequestDto originalRequest = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .prescriptionNumber("RX-100")
                .doctorName("Dr. Alpha")
                .doctorRegNumber("REG-001")
                .build();

        Reservation existingReservation = Reservation.builder()
                .id(800L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .build();

        com.sentragrid.entity.IdempotencyRecord record = com.sentragrid.entity.IdempotencyRecord.builder()
                .id(1L)
                .user(patient)
                .idempotencyKey("KEY-123")
                .requestHash("different-request-hash-value")
                .reservation(existingReservation)
                .build();

        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, "KEY-123")).thenReturn(Optional.of(record));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(1L, originalRequest, "KEY-123"));
        assertTrue(ex.getMessage().contains("Idempotency key was previously used with a different reservation request"));
    }

    @Test
    @DisplayName("Step 4 Hardening - Confirm pickup is rejected if reservation has expired by server clock")
    void testConfirmPickup_ExpiredReservation_ThrowsBadRequestException() {
        Reservation expiredReservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .prescriptionVerified(true)
                .prescriptionVerifiedBy("pharmacist1")
                .prescriptionVerifiedAt(LocalDateTime.now().minusMinutes(10))
                .build();

        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();

        inventory.setQuantity(10);
        inventory.setReservedQuantity(2);

        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(reservationRepository.findById(500L)).thenReturn(Optional.of(expiredReservation));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reservationService.confirmPickup(500L, "pharmacist1"));
        assertTrue(ex.getMessage().contains("Cannot confirm pickup: Reservation has expired"),
                "Must reject pickup confirmation when reservation has expired");

        // Verify inventory is untouched and no state changes persisted
        assertEquals(10, inventory.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());
        verify(inventoryRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(kafkaProducerService, never()).sendReservationConfirmedEvent(any());
    }

    @Test
    @DisplayName("Step 5C.3 - Pharmacist can view reservations for assigned pharmacy branch")
    void testGetPharmacyReservations_Success_ForAssignedStaff() {
        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(pharmacyRepository.existsById(10L)).thenReturn(true);

        Reservation res = Reservation.builder()
                .id(501L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .prescriptionVerified(false)
                .prescriptionNumber("RX-999")
                .doctorName("Dr. Smith")
                .build();

        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(res));

        List<ReservationResponseDto> results = reservationService.getPharmacyReservations(10L, null, "pharmacist1");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(501L, results.get(0).getId());
        assertEquals("RX-999", results.get(0).getPrescriptionNumber());
        assertEquals("Dr. Smith", results.get(0).getDoctorName());
    }

    @Test
    @DisplayName("Step 5C.3 - Pharmacist denied access when requesting another pharmacy branch")
    void testGetPharmacyReservations_Denied_ForDifferentBranchStaff() {
        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));

        assertThrows(AccessDeniedException.class,
                () -> reservationService.getPharmacyReservations(99L, null, "pharmacist1"));
        verify(reservationRepository, never()).findByPharmacyIdOrderByCreatedAtDesc(anyLong());
    }

    @Test
    @DisplayName("Step 5C.3 - Admin can view reservations for any pharmacy branch")
    void testGetPharmacyReservations_Allowed_ForAdmin() {
        User admin = User.builder().id(3L).username("admin1").role(UserRole.ADMIN).build();
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(admin));
        when(pharmacyRepository.existsById(10L)).thenReturn(true);

        Reservation res = Reservation.builder()
                .id(502L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .build();

        when(reservationRepository.findByPharmacyIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(res));

        List<ReservationResponseDto> results = reservationService.getPharmacyReservations(10L, null, "admin1");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(502L, results.get(0).getId());
    }

    @Test
    @DisplayName("Step 5C.3 - Filtering pharmacy reservations by status")
    void testGetPharmacyReservations_WithStatusFilter() {
        User pharmacist = User.builder().id(2L).username("pharmacist1").role(UserRole.PHARMACIST).pharmacy(pharmacy).build();
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist));
        when(pharmacyRepository.existsById(10L)).thenReturn(true);

        Reservation res = Reservation.builder()
                .id(503L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build();

        when(reservationRepository.findByPharmacyIdAndStatusOrderByCreatedAtDesc(10L, ReservationStatus.PENDING))
                .thenReturn(List.of(res));

        List<ReservationResponseDto> results = reservationService.getPharmacyReservations(10L, ReservationStatus.PENDING, "pharmacist1");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(503L, results.get(0).getId());
        verify(reservationRepository, times(1)).findByPharmacyIdAndStatusOrderByCreatedAtDesc(10L, ReservationStatus.PENDING);
        verify(reservationRepository, never()).findByPharmacyIdOrderByCreatedAtDesc(anyLong());
    }
}
