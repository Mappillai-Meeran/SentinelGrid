package com.sentragrid.config;

import com.sentragrid.common.AppConstants;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    @Bean
    public NewTopic reservationCreatedTopic() {
        return TopicBuilder.name(AppConstants.RESERVATION_CREATED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reservationExpiredTopic() {
        return TopicBuilder.name(AppConstants.RESERVATION_EXPIRED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
