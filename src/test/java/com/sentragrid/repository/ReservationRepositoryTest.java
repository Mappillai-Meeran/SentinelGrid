package com.sentragrid.repository;

import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Test
    @DisplayName("Find Expired Reservations Query Test")
    void testFindExpiredReservations() {
        User patient = userRepository.save(User.builder()
                .username("testpatient")
                .email("testpatient@example.com")
                .password("password")
                .role(UserRole.PATIENT)
                .build());

        Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
                .name("Test Pharmacy")
                .address("123 Street")
                .city("Bangalore")
                .contactNumber("1234567890")
                .build());

        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Test Med")
                .category("ANTIBIOTIC")
                .requiresPrescription(false)
                .build());

        // Expired reservation
        reservationRepository.save(Reservation.builder()
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(10))
                .build());

        // Active reservation
        reservationRepository.save(Reservation.builder()
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(1)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .build());

        List<Reservation> expiredList = reservationRepository.findExpiredReservations(
                ReservationStatus.PENDING, LocalDateTime.now());

        assertFalse(expiredList.isEmpty(), "Should find at least one expired reservation");
        assertTrue(expiredList.get(0).getExpiresAt().isBefore(LocalDateTime.now()));
    }

    @Test
    @DisplayName("Performance Aggregation - Correctly groups and tallies confirmed, cancelled, and expired counts per branch")
    void testFindTopPharmaciesPerformanceAggregates() {
        User patient = userRepository.save(User.builder()
                .username("aggpatient")
                .email("aggpatient@example.com")
                .password("password")
                .role(UserRole.PATIENT)
                .build());

        Pharmacy p1 = pharmacyRepository.save(Pharmacy.builder()
                .name("Agg Pharmacy A")
                .address("Road A")
                .city("Chennai")
                .contactNumber("1111111111")
                .is24Seven(true)
                .build());

        Pharmacy p2 = pharmacyRepository.save(Pharmacy.builder()
                .name("Agg Pharmacy B")
                .address("Road B")
                .city("Chennai")
                .contactNumber("2222222222")
                .is24Seven(false)
                .build());

        Medicine med = medicineRepository.save(Medicine.builder()
                .name("Agg Medicine")
                .category("ANTIVIRAL")
                .requiresPrescription(false)
                .build());

        // P1: 2 confirmed, 1 cancelled, 1 expired = 4 total, 4 concluded
        reservationRepository.save(Reservation.builder().patient(patient).pharmacy(p1).medicine(med).quantity(1).status(ReservationStatus.CONFIRMED).expiresAt(LocalDateTime.now().plusHours(1)).build());
        reservationRepository.save(Reservation.builder().patient(patient).pharmacy(p1).medicine(med).quantity(1).status(ReservationStatus.CONFIRMED).expiresAt(LocalDateTime.now().plusHours(1)).build());
        reservationRepository.save(Reservation.builder().patient(patient).pharmacy(p1).medicine(med).quantity(1).status(ReservationStatus.CANCELLED).expiresAt(LocalDateTime.now().minusHours(1)).build());
        reservationRepository.save(Reservation.builder().patient(patient).pharmacy(p1).medicine(med).quantity(1).status(ReservationStatus.EXPIRED).expiresAt(LocalDateTime.now().minusHours(1)).build());

        // P2: 1 confirmed, 1 pending = 2 total, 1 concluded
        reservationRepository.save(Reservation.builder().patient(patient).pharmacy(p2).medicine(med).quantity(1).status(ReservationStatus.CONFIRMED).expiresAt(LocalDateTime.now().plusHours(1)).build());
        reservationRepository.save(Reservation.builder().patient(patient).pharmacy(p2).medicine(med).quantity(1).status(ReservationStatus.PENDING).expiresAt(LocalDateTime.now().plusMinutes(20)).build());

        List<Object[]> aggregates = reservationRepository.findTopPharmaciesPerformanceAggregates(
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertNotNull(aggregates);
        assertTrue(aggregates.size() >= 2);

        // First item should be Pharmacy A (4 reservations)
        Object[] rowA = aggregates.stream()
                .filter(row -> "Agg Pharmacy A".equals(row[0]))
                .findFirst().orElseThrow();
        assertEquals("Agg Pharmacy A", rowA[0]);
        assertEquals(4L, ((Number) rowA[1]).longValue()); // total
        assertEquals(2L, ((Number) rowA[2]).longValue()); // confirmed
        assertEquals(1L, ((Number) rowA[3]).longValue()); // cancelled
        assertEquals(1L, ((Number) rowA[4]).longValue()); // expired

        // Second item should be Pharmacy B (2 reservations)
        Object[] rowB = aggregates.stream()
                .filter(row -> "Agg Pharmacy B".equals(row[0]))
                .findFirst().orElseThrow();
        assertEquals("Agg Pharmacy B", rowB[0]);
        assertEquals(2L, ((Number) rowB[1]).longValue()); // total
        assertEquals(1L, ((Number) rowB[2]).longValue()); // confirmed
        assertEquals(0L, ((Number) rowB[3]).longValue()); // cancelled
        assertEquals(0L, ((Number) rowB[4]).longValue()); // expired

        // Test countByIs24Seven
        assertTrue(pharmacyRepository.countByIs24Seven(true) >= 1);
        assertTrue(pharmacyRepository.countByIs24Seven(false) >= 1);
    }
}
