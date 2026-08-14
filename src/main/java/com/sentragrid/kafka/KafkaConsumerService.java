package com.sentragrid.kafka;

import com.sentragrid.common.AppConstants;
import com.sentragrid.kafka.event.ReservationCreatedEvent;
import com.sentragrid.kafka.event.ReservationExpiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaConsumerService {

    @KafkaListener(topics = AppConstants.RESERVATION_CREATED_TOPIC, groupId = "${spring.kafka.consumer.group-id:sentinelgrid-group}")
    public void consumeReservationCreated(ReservationCreatedEvent event) {
        log.info("[Kafka Consumer] Received ReservationCreatedEvent for Reservation ID: {}, Patient ID: {}, Pharmacy ID: {}",
                event.getReservationId(), event.getPatientId(), event.getPharmacyId());
    }

    @KafkaListener(topics = AppConstants.RESERVATION_EXPIRED_TOPIC, groupId = "${spring.kafka.consumer.group-id:sentinelgrid-group}")
    public void consumeReservationExpired(ReservationExpiredEvent event) {
        log.info("[Kafka Consumer] Received ReservationExpiredEvent for Reservation ID: {}, Released Qty: {}",
                event.getReservationId(), event.getQuantityReleased());
    }
}
