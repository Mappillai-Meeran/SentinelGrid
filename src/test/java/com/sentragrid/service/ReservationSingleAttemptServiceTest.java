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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationSingleAttemptServiceTest {

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
    private com.sentragrid.repository.IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private com.sentragrid.manager.repository.ManagerSettingsRepository managerSettingsRepository;

    @InjectMocks
    private ReservationSingleAttemptService singleAttemptService;

    private User patient;
    private Pharmacy pharmacy;
    private Medicine medicine;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        patient = User.builder().id(1L).username("john_doe").role(UserRole.PATIENT).build();
        pharmacy = Pharmacy.builder().id(10L).name("Apollo").city("Bangalore").contactNumber("1234").build();
        medicine = Medicine.builder().id(100L).name("Remdesivir").category("ANTIVIRAL").requiresPrescription(true).build();
        inventory = Inventory.builder().id(1000L).pharmacy(pharmacy).medicine(medicine).quantity(10).reservedQuantity(0).version(0L).build();
    }

    @Test
    @DisplayName("Single Attempt - Create Reservation Success")
    void testExecuteSingleAttempt_Success() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .prescriptionNumber("RX-12345")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-98765")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);

        Reservation savedReservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .version(0L)
                .build();

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);

        ReservationResponseDto response = singleAttemptService.executeSingleAttempt(1L, request, 30, null, null);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(ReservationStatus.PENDING, response.getStatus());
        assertEquals(2, response.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());

        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("RESERVATION_CREATED"), anyString(), anyString());
        verify(kafkaProducerService, times(1)).sendReservationCreatedEvent(any());
    }

    @Test
    @DisplayName("Single Attempt - Fails when active reservation exists")
    void testExecuteSingleAttempt_ActiveReservationExists() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .prescriptionNumber("RX-12345")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-98765")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> singleAttemptService.executeSingleAttempt(1L, request, 30, null, null));
    }

    @Test
    @DisplayName("Single Attempt - Fails on Insufficient Stock")
    void testExecuteSingleAttempt_InsufficientStock() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(15)
                .prescriptionNumber("RX-12345")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-98765")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));

        assertThrows(BadRequestException.class, () -> singleAttemptService.executeSingleAttempt(1L, request, 30, null, null));
    }

    @Test
    @DisplayName("Step 4 - Non-prescription reservation succeeds without prescription details")
    void testExecuteSingleAttempt_NonPrescriptionSuccess() {
        Medicine otcMedicine = Medicine.builder()
                .id(101L)
                .name("Favipiravir 400mg")
                .category("ANTIVIRAL")
                .requiresPrescription(false)
                .build();

        Inventory otcInventory = Inventory.builder()
                .id(1001L)
                .pharmacy(pharmacy)
                .medicine(otcMedicine)
                .quantity(10)
                .reservedQuantity(0)
                .version(0L)
                .build();

        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(101L)
                .quantity(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(101L)).thenReturn(Optional.of(otcMedicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 101L)).thenReturn(Optional.of(otcInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(otcInventory);

        Reservation savedReservation = Reservation.builder()
                .id(501L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(otcMedicine)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .prescriptionVerified(false)
                .version(0L)
                .build();

        when(reservationRepository.save(any(Reservation.class))).thenReturn(savedReservation);

        ReservationResponseDto response = singleAttemptService.executeSingleAttempt(1L, request, 30, null, null);

        assertNotNull(response);
        assertEquals(501L, response.getId());
        assertFalse(response.isPrescriptionVerified());
        assertNull(response.getPrescriptionNumber());
    }

    @Test
    @DisplayName("Step 4 - Prescription-required reservation fails when details are missing or blank")
    void testExecuteSingleAttempt_MissingPrescriptionDetailsFails() {
        ReservationRequestDto missingRequest = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(1)
                .prescriptionNumber("   ")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-12345")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> singleAttemptService.executeSingleAttempt(1L, missingRequest, 30, null, null));
        assertTrue(ex.getMessage().contains("Prescription details"), "Must enforce non-blank prescription metadata");
    }

    @Test
    @DisplayName("Step 4 - Client cannot set prescriptionVerified=true at creation")
    void testExecuteSingleAttempt_ClientCannotSetPrescriptionVerified() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(1)
                .prescriptionNumber("RX-777")
                .doctorName("Dr. Smith")
                .doctorRegNumber("REG-888")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);

        // Capture the entity passed to reservationRepository.save
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            assertFalse(r.isPrescriptionVerified(), "Entity must be created with prescriptionVerified=false");
            assertNull(r.getPrescriptionVerifiedBy());
            assertNull(r.getPrescriptionVerifiedAt());
            r.setId(502L);
            return r;
        });

        ReservationResponseDto response = singleAttemptService.executeSingleAttempt(1L, request, 30, null, null);
        assertNotNull(response);
        assertFalse(response.isPrescriptionVerified(), "Response must indicate prescription is unverified");
    }

    @Test
    @DisplayName("Branch Rules - Fails when requested quantity exceeds Manager maximum emergency hold limit")
    void testExecuteSingleAttempt_ExceedsBranchMaxEmergencyHold() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(3)
                .prescriptionNumber("RX-12345")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-98765")
                .build();

        com.sentragrid.manager.entity.ManagerSettings settings = com.sentragrid.manager.entity.ManagerSettings.builder()
                .id(10L)
                .pharmacyName("Apollo")
                .holdExpiryMinutes(25)
                .maxEmergencyHoldQuantity(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(managerSettingsRepository.findById(10L)).thenReturn(Optional.of(settings));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> singleAttemptService.executeSingleAttempt(1L, request, 30, null, null));
        assertTrue(ex.getMessage().contains("exceeds branch maximum emergency hold limit"),
                "Must reject reservation when quantity exceeds branch manager rule");
    }

    @Test
    @DisplayName("Branch Rules - Applies Manager branch-configured hold expiry TTL")
    void testExecuteSingleAttempt_AppliesBranchHoldExpiryMinutes() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .prescriptionNumber("RX-12345")
                .doctorName("Dr. Sharma")
                .doctorRegNumber("MCI-98765")
                .build();

        com.sentragrid.manager.entity.ManagerSettings settings = com.sentragrid.manager.entity.ManagerSettings.builder()
                .id(10L)
                .pharmacyName("Apollo")
                .holdExpiryMinutes(15)
                .maxEmergencyHoldQuantity(5)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenReturn(inventory);
        when(managerSettingsRepository.findById(10L)).thenReturn(Optional.of(settings));

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation r = invocation.getArgument(0);
            r.setId(503L);
            return r;
        });

        ReservationResponseDto response = singleAttemptService.executeSingleAttempt(1L, request, 30, null, null);
        assertNotNull(response);
        assertNotNull(response.getRemainingSeconds());
        assertTrue(response.getRemainingSeconds() <= 15 * 60, "Remaining seconds should be bounded by branch 15-minute TTL");
        assertTrue(response.getRemainingSeconds() > 14 * 60, "Remaining seconds should reflect ~15 minutes");
    }
}
