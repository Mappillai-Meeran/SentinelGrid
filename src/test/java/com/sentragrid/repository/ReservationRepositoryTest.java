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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("sentinelgrid_test")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Test
    @DisplayName("Testcontainers PostgreSQL - Find Expired Reservations")
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

        assertEquals(1, expiredList.size());
        assertTrue(expiredList.get(0).getExpiresAt().isBefore(LocalDateTime.now()));
    }
}
