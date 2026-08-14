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

import java.time.LocalDateTime;
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
    @DisplayName("Create Reservation - Success")
    void testCreateReservation_Success() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
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

        ReservationResponseDto response = reservationService.createReservation(1L, request);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(ReservationStatus.PENDING, response.getStatus());
        assertEquals(2, response.getQuantity());
        assertEquals(2, inventory.getReservedQuantity());

        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("RESERVATION_CREATED"), anyString(), anyString());
        verify(kafkaProducerService, times(1)).sendReservationCreatedEvent(any());
    }

    @Test
    @DisplayName("Create Reservation - Fails when active reservation exists in same category")
    void testCreateReservation_ActiveReservationExists() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(1L, request));
    }

    @Test
    @DisplayName("Create Reservation - Insufficient Stock")
    void testCreateReservation_InsufficientStock() {
        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(10L)
                .medicineId(100L)
                .quantity(15)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(pharmacyRepository.findById(10L)).thenReturn(Optional.of(pharmacy));
        when(medicineRepository.findById(100L)).thenReturn(Optional.of(medicine));
        when(reservationRepository.existsActiveReservationForPatientAndCategory(1L, "ANTIVIRAL")).thenReturn(false);
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(1L, request));
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
                .build();

        inventory.setReservedQuantity(2);

        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        ReservationResponseDto response = reservationService.cancelReservation(500L, "john_doe");

        assertEquals(ReservationStatus.CANCELLED, response.getStatus());
        assertEquals(0, inventory.getReservedQuantity());
        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("RESERVATION_CANCELLED"), eq("john_doe"), anyString());
    }

    @Test
    @DisplayName("Confirm Pickup - Success")
    void testConfirmPickup_Success() {
        Reservation reservation = Reservation.builder()
                .id(500L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .build();

        inventory.setQuantity(10);
        inventory.setReservedQuantity(2);

        when(reservationRepository.findById(500L)).thenReturn(Optional.of(reservation));
        when(inventoryRepository.findByPharmacyIdAndMedicineId(10L, 100L)).thenReturn(Optional.of(inventory));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(reservation);

        ReservationResponseDto response = reservationService.confirmPickup(500L, "pharmacist1");

        assertEquals(ReservationStatus.CONFIRMED, response.getStatus());
        assertEquals(8, inventory.getQuantity());
        assertEquals(0, inventory.getReservedQuantity());
        verify(auditLogService, times(1)).logAction(eq("Reservation"), eq(500L), eq("RESERVATION_CONFIRMED"), eq("pharmacist1"), anyString());
    }
}
