package com.sentragrid.service;

import com.sentragrid.dto.ReservationRequestDto;
import com.sentragrid.dto.ReservationResponseDto;
import com.sentragrid.entity.IdempotencyRecord;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.repository.IdempotencyRecordRepository;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

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
    private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock
    private ReservationSingleAttemptService singleAttemptService;

    @InjectMocks
    private ReservationService reservationService;

    private User patient;
    private Pharmacy pharmacy;
    private Medicine medicine;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "defaultTtlMinutes", 30);
        patient = User.builder().id(1L).username("patient1").role(UserRole.PATIENT).build();
        pharmacy = Pharmacy.builder().id(10L).name("Apollo").build();
        medicine = Medicine.builder().id(100L).name("Remdesivir").category("ANTIVIRAL").build();
    }

    @Test
    @DisplayName("Idempotency - Repeat request with SAME key and SAME payload returns original reservation")
    void testSameKey_SamePayload_ReturnsExistingReservation() {
        ReservationRequestDto request = ReservationRequestDto.builder().pharmacyId(10L).medicineId(100L).quantity(2).build();
        String idempotencyKey = "key-12345";

        Reservation existingReservation = Reservation.builder()
                .id(999L)
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(2)
                .status(ReservationStatus.PENDING)
                .build();

        // SHA-256 of "10:100:2"
        String expectedHash = computeExpectedHash("10:100:2");

        IdempotencyRecord record = IdempotencyRecord.builder()
                .id(1L)
                .user(patient)
                .idempotencyKey(idempotencyKey)
                .requestHash(expectedHash)
                .reservation(existingReservation)
                .build();

        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(record));

        ReservationResponseDto response = reservationService.createReservation(1L, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(999L, response.getId());
        verify(singleAttemptService, never()).executeSingleAttempt(anyLong(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Idempotency - Request with SAME key but DIFFERENT payload is rejected with BadRequestException")
    void testSameKey_DifferentPayload_ThrowsBadRequestException() {
        ReservationRequestDto originalRequest = ReservationRequestDto.builder().pharmacyId(10L).medicineId(100L).quantity(2).build();
        ReservationRequestDto differentRequest = ReservationRequestDto.builder().pharmacyId(10L).medicineId(100L).quantity(5).build();
        String idempotencyKey = "key-12345";

        Reservation existingReservation = Reservation.builder().id(999L).patient(patient).pharmacy(pharmacy).medicine(medicine).build();
        String originalHash = computeExpectedHash("10:100:2");

        IdempotencyRecord record = IdempotencyRecord.builder()
                .id(1L)
                .user(patient)
                .idempotencyKey(idempotencyKey)
                .requestHash(originalHash)
                .reservation(existingReservation)
                .build();

        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.of(record));

        assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(1L, differentRequest, idempotencyKey));

        verify(singleAttemptService, never()).executeSingleAttempt(anyLong(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Idempotency - Excessive key length (>255) is rejected")
    void testExcessiveKeyLength_ThrowsBadRequestException() {
        ReservationRequestDto request = ReservationRequestDto.builder().pharmacyId(10L).medicineId(100L).quantity(2).build();
        String longKey = "a".repeat(256);

        assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(1L, request, longKey));
    }

    @Test
    @DisplayName("Idempotency - Keys are isolated per authenticated user (TEST 4)")
    void testSameKey_DifferentUsers_Isolated() {
        ReservationRequestDto request = ReservationRequestDto.builder().pharmacyId(10L).medicineId(100L).quantity(2).build();
        String idempotencyKey = "shared-key-123";

        // Patient 1 has an existing record
        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(2L, idempotencyKey))
                .thenReturn(Optional.empty());

        ReservationResponseDto expectedResponse = ReservationResponseDto.builder().id(888L).status(ReservationStatus.PENDING).build();
        when(singleAttemptService.executeSingleAttempt(eq(2L), eq(request), eq(30), eq(idempotencyKey), anyString()))
                .thenReturn(expectedResponse);

        // Patient 2 submits the same key
        ReservationResponseDto response = reservationService.createReservation(2L, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(888L, response.getId());
        verify(idempotencyRecordRepository, times(1)).findByUserIdAndIdempotencyKey(2L, idempotencyKey);
        verify(singleAttemptService, times(1)).executeSingleAttempt(eq(2L), eq(request), eq(30), eq(idempotencyKey), anyString());
    }

    @Test
    @DisplayName("Idempotency - DataIntegrityViolation fallback queries and returns committed record (TEST 5)")
    void testDataIntegrityViolation_QueriesAndReturnsExistingRecord() {
        ReservationRequestDto request = ReservationRequestDto.builder().pharmacyId(10L).medicineId(100L).quantity(2).build();
        String idempotencyKey = "race-key-456";

        Reservation existingReservation = Reservation.builder().id(777L).patient(patient).pharmacy(pharmacy).medicine(medicine).build();
        String expectedHash = computeExpectedHash("10:100:2");

        IdempotencyRecord record = IdempotencyRecord.builder()
                .id(10L)
                .user(patient)
                .idempotencyKey(idempotencyKey)
                .requestHash(expectedHash)
                .reservation(existingReservation)
                .build();

        // Initial lookup finds empty (concurrent creation in progress)
        when(idempotencyRecordRepository.findByUserIdAndIdempotencyKey(1L, idempotencyKey))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(record));

        when(singleAttemptService.executeSingleAttempt(eq(1L), eq(request), eq(30), eq(idempotencyKey), anyString()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

        ReservationResponseDto response = reservationService.createReservation(1L, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(777L, response.getId());
    }

    private String computeExpectedHash(String raw) {
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
        } catch (Exception e) {
            return "";
        }
    }
}
