package com.sentragrid.dashboard.service;

import com.sentragrid.dashboard.dto.DashboardSummaryDto;
import com.sentragrid.dashboard.dto.TopPharmacyDto;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PharmacyRepository pharmacyRepository;

    @Mock
    private MedicineRepository medicineRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("Fulfillment Rate - Correct completed, cancelled, expired, concluded count, and fulfillment rate")
    void testGetTopPharmacies_CalculatesFulfillmentRateCorrectly() {
        // row: pharmacyName, totalCount, completedCount, cancelledCount, expiredCount
        List<Object[]> mockRows = new ArrayList<>();
        // Pharmacy 1: 10 completed, 2 cancelled, 3 expired => concluded = 15 => rate = 10/15*100 = 66.7%
        mockRows.add(new Object[]{"Apollo M.G. Road", 20L, 10L, 2L, 3L});
        // Pharmacy 2: 5 completed, 0 cancelled, 0 expired => concluded = 5 => rate = 100.0%
        mockRows.add(new Object[]{"Mission Street Emergency", 8L, 5L, 0L, 0L});

        when(reservationRepository.findTopPharmaciesPerformanceAggregates(any(Pageable.class)))
                .thenReturn(mockRows);

        List<TopPharmacyDto> results = dashboardService.getTopPharmacies(5);

        assertThat(results).hasSize(2);

        TopPharmacyDto p1 = results.get(0);
        assertThat(p1.pharmacyName()).isEqualTo("Apollo M.G. Road");
        assertThat(p1.reservationCount()).isEqualTo(20L);
        assertThat(p1.completedCount()).isEqualTo(10L);
        assertThat(p1.concludedCount()).isEqualTo(15L);
        assertThat(p1.fulfillmentRate()).isEqualTo(66.7);

        TopPharmacyDto p2 = results.get(1);
        assertThat(p2.pharmacyName()).isEqualTo("Mission Street Emergency");
        assertThat(p2.reservationCount()).isEqualTo(8L);
        assertThat(p2.completedCount()).isEqualTo(5L);
        assertThat(p2.concludedCount()).isEqualTo(5L);
        assertThat(p2.fulfillmentRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Fulfillment Rate - Zero concluded reservations returns null fulfillment rate")
    void testGetTopPharmacies_ZeroConcludedReturnsNullRate() {
        // Pharmacy with 4 active pending holds, but 0 completed, 0 cancelled, 0 expired
        List<Object[]> mockRows = new ArrayList<>();
        mockRows.add(new Object[]{"Rajiv Gandhi WCH", 4L, 0L, 0L, 0L});

        when(reservationRepository.findTopPharmaciesPerformanceAggregates(any(Pageable.class)))
                .thenReturn(mockRows);

        List<TopPharmacyDto> results = dashboardService.getTopPharmacies(5);

        assertThat(results).hasSize(1);
        TopPharmacyDto p = results.get(0);
        assertThat(p.pharmacyName()).isEqualTo("Rajiv Gandhi WCH");
        assertThat(p.reservationCount()).isEqualTo(4L);
        assertThat(p.completedCount()).isEqualTo(0L);
        assertThat(p.concludedCount()).isEqualTo(0L);
        assertThat(p.fulfillmentRate()).isNull();
    }

    @Test
    @DisplayName("Fulfillment Rate - Empty or no reservations returns empty list without error")
    void testGetTopPharmacies_EmptyReservationsReturnsEmptyList() {
        when(reservationRepository.findTopPharmaciesPerformanceAggregates(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<TopPharmacyDto> results = dashboardService.getTopPharmacies(5);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Fulfillment Rate - Negative or zero limit defaults to 5")
    void testGetTopPharmacies_InvalidLimitHandledGracefully() {
        when(reservationRepository.findTopPharmaciesPerformanceAggregates(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<TopPharmacyDto> results = dashboardService.getTopPharmacies(-1);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Summary Metrics - Returns genuine 24/7 and standard hours branch breakdown")
    void testGetSummaryMetrics_Includes24SevenAndStandardHoursCount() {
        when(userRepository.count()).thenReturn(50L);
        when(pharmacyRepository.count()).thenReturn(15L);
        when(pharmacyRepository.countByIs24Seven(true)).thenReturn(4L);
        when(medicineRepository.count()).thenReturn(20L);
        when(reservationRepository.count()).thenReturn(100L);
        when(reservationRepository.countByStatus(ReservationStatus.PENDING)).thenReturn(10L);
        when(reservationRepository.countByStatus(ReservationStatus.CONFIRMED)).thenReturn(60L);
        when(reservationRepository.countByStatus(ReservationStatus.EXPIRED)).thenReturn(20L);
        when(reservationRepository.countByStatus(ReservationStatus.CANCELLED)).thenReturn(10L);
        when(inventoryRepository.sumTotalQuantity()).thenReturn(500L);

        DashboardSummaryDto summary = dashboardService.getSummaryMetrics();

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalPharmacies()).isEqualTo(15L);
        assertThat(summary.getTwentyFourSevenPharmacies()).isEqualTo(4L);
        assertThat(summary.getStandardHoursPharmacies()).isEqualTo(11L); // 15 - 4 = 11
        assertThat(summary.getPendingReservations()).isEqualTo(10L);
        assertThat(summary.getConfirmedReservations()).isEqualTo(60L);
        assertThat(summary.getTotalInventoryUnits()).isEqualTo(500L);
    }
}
