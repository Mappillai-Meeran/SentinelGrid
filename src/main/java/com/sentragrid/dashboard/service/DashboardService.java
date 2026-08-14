package com.sentragrid.dashboard.service;

import com.sentragrid.dashboard.dto.DailyReservationDto;
import com.sentragrid.dashboard.dto.DashboardSummaryDto;
import com.sentragrid.dashboard.dto.LowStockInventoryDto;
import com.sentragrid.dashboard.dto.TopMedicineDto;
import com.sentragrid.dashboard.dto.TopPharmacyDto;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final MedicineRepository medicineRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryDto getSummaryMetrics() {
        return DashboardSummaryDto.builder()
                .totalUsers(userRepository.count())
                .totalPharmacies(pharmacyRepository.count())
                .totalMedicines(medicineRepository.count())
                .totalReservations(reservationRepository.count())
                .pendingReservations(reservationRepository.countByStatus(ReservationStatus.PENDING))
                .confirmedReservations(reservationRepository.countByStatus(ReservationStatus.CONFIRMED))
                .expiredReservations(reservationRepository.countByStatus(ReservationStatus.EXPIRED))
                .cancelledReservations(reservationRepository.countByStatus(ReservationStatus.CANCELLED))
                .totalInventoryUnits(inventoryRepository.sumTotalQuantity())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TopMedicineDto> getTopReservedMedicines(int limit) {
        int fetchLimit = limit <= 0 ? 5 : limit;
        return reservationRepository.findTopReservedMedicines(PageRequest.of(0, fetchLimit));
    }

    @Transactional(readOnly = true)
    public List<TopPharmacyDto> getTopPharmacies(int limit) {
        int fetchLimit = limit <= 0 ? 5 : limit;
        return reservationRepository.findTopPharmaciesByReservations(PageRequest.of(0, fetchLimit));
    }

    @Transactional(readOnly = true)
    public List<DailyReservationDto> getDailyReservationTrend(int days) {
        int daysBack = days <= 0 ? 7 : days;
        LocalDateTime startDate = LocalDateTime.now().minusDays(daysBack).withHour(0).withMinute(0).withSecond(0);

        List<Object[]> results = reservationRepository.findDailyReservationCounts(startDate);
        List<DailyReservationDto> trend = new ArrayList<>();

        for (Object[] row : results) {
            LocalDate date;
            if (row[0] instanceof Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else if (row[0] instanceof LocalDate localDate) {
                date = localDate;
            } else {
                date = LocalDate.parse(row[0].toString());
            }
            Long count = ((Number) row[1]).longValue();
            trend.add(new DailyReservationDto(date, count));
        }

        return trend;
    }

    @Transactional(readOnly = true)
    public List<LowStockInventoryDto> getLowStockInventory(int threshold) {
        int limitThreshold = threshold <= 0 ? 10 : threshold;
        return inventoryRepository.findLowStockInventory(limitThreshold).stream()
                .map(inv -> {
                    int available = inv.getQuantity() - inv.getReservedQuantity();
                    return LowStockInventoryDto.builder()
                            .inventoryId(inv.getId())
                            .pharmacyName(inv.getPharmacy().getName())
                            .medicineName(inv.getMedicine().getName())
                            .quantity(inv.getQuantity())
                            .reservedQuantity(inv.getReservedQuantity())
                            .availableQuantity(available)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
