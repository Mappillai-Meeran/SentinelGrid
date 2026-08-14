package com.sentragrid.scheduler;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.kafka.KafkaProducerService;
import com.sentragrid.kafka.event.ReservationExpiredEvent;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job for automatic TTL reservation expiry and stock rollback.
 * Runs periodically to release reserved medicine stock for uncollected reservations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogService auditLogService;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(cron = "${app.reservation.expiry-cron:0 */1 * * * *}")
    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public void processExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(ReservationStatus.PENDING, now);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("[ReservationExpiryScheduler] Found {} expired pending reservations to process.", expiredReservations.size());

        for (Reservation reservation : expiredReservations) {
            try {
                reservation.setStatus(ReservationStatus.EXPIRED);

                // Release reserved stock in Inventory
                inventoryRepository.findByPharmacyIdAndMedicineId(
                        reservation.getPharmacy().getId(), reservation.getMedicine().getId())
                        .ifPresent(inventory -> {
                            inventory.setReservedQuantity(Math.max(0, inventory.getReservedQuantity() - reservation.getQuantity()));
                            inventoryRepository.save(inventory);
                        });

                reservationRepository.save(reservation);

                auditLogService.logAction("Reservation", reservation.getId(), "RESERVATION_EXPIRED", "SYSTEM_SCHEDULER",
                        "Reservation TTL expired. Released " + reservation.getQuantity() + " reserved units.");

                // Kafka Event
                ReservationExpiredEvent event = ReservationExpiredEvent.builder()
                        .reservationId(reservation.getId())
                        .patientId(reservation.getPatient().getId())
                        .pharmacyId(reservation.getPharmacy().getId())
                        .medicineId(reservation.getMedicine().getId())
                        .quantityReleased(reservation.getQuantity())
                        .expiredAt(now)
                        .build();

                kafkaProducerService.sendReservationExpiredEvent(event);

            } catch (Exception ex) {
                log.error("Failed to expire reservation ID: {}", reservation.getId(), ex);
            }
        }
    }
}
