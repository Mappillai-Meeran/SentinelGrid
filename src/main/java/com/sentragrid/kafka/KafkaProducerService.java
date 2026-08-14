package com.sentragrid.kafka;

import com.sentragrid.common.AppConstants;
import com.sentragrid.kafka.event.ReservationCreatedEvent;
import com.sentragrid.kafka.event.ReservationExpiredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    public KafkaProducerService(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendReservationCreatedEvent(ReservationCreatedEvent event) {
        if (kafkaTemplate == null) {
            log.info("Kafka is disabled. Skipping ReservationCreatedEvent publishing: {}", event);
            return;
        }
        try {
            log.info("Publishing ReservationCreatedEvent to Kafka: {}", event);
            kafkaTemplate.send(AppConstants.RESERVATION_CREATED_TOPIC, String.valueOf(event.getReservationId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish ReservationCreatedEvent to Kafka: {}", e.getMessage());
        }
    }

    public void sendReservationExpiredEvent(ReservationExpiredEvent event) {
        if (kafkaTemplate == null) {
            log.info("Kafka is disabled. Skipping ReservationExpiredEvent publishing: {}", event);
            return;
        }
        try {
            log.info("Publishing ReservationExpiredEvent to Kafka: {}", event);
            kafkaTemplate.send(AppConstants.RESERVATION_EXPIRED_TOPIC, String.valueOf(event.getReservationId()), event);
        } catch (Exception e) {
            log.warn("Failed to publish ReservationExpiredEvent to Kafka: {}", e.getMessage());
        }
    }
}
