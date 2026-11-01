package com.sentragrid.service;

import com.sentragrid.config.DemoDataInitializer;
import com.sentragrid.config.KafkaConfig;
import com.sentragrid.config.ProfileSafetyValidator;
import com.sentragrid.config.RedisConfig;
import com.sentragrid.kafka.KafkaConsumerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sentinelgrid",
        "spring.datasource.username=postgres",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:}",
        "app.jwt.secret=CloudDemoSecretKeyMustBeAtLeast32BytesLongForHmacSha256Verification!"
})
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@DisplayName("Phase 5B - Cloud Demo Profile Infrastructure & Safety Test")
class CloudDemoProfileSafetyTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Cloud demo profile: KafkaConfig and KafkaConsumerService beans are strictly excluded")
    void testKafkaBeansExcludedInCloudDemo() {
        assertFalse(applicationContext.containsBean("kafkaConfig"),
                "KafkaConfig bean must NOT be present in cloud demo context when Kafka is disabled");
        assertFalse(applicationContext.containsBean("kafkaConsumerService"),
                "KafkaConsumerService bean must NOT be present in cloud demo context when Kafka is disabled");
    }

    @Test
    @DisplayName("Cloud demo profile: RedisConfig bean is strictly excluded")
    void testRedisConfigExcludedInCloudDemo() {
        assertFalse(applicationContext.containsBean("redisConfig"),
                "RedisConfig bean must NOT be present in cloud demo context when Redis is disabled");
    }

    @Test
    @DisplayName("Cloud demo profile: DemoDataInitializer is strictly excluded")
    void testDemoDataInitializerAbsentInCloudDemo() {
        assertFalse(applicationContext.containsBean("demoDataInitializer"),
                "DemoDataInitializer (@Profile('local')) must NOT be registered in cloud demo context");
    }

    @Test
    @DisplayName("Cloud demo profile: Hikari connection pool is tuned for Neon Free tier (max-pool-size=5)")
    void testHikariConnectionPoolTunedForFreeTier() {
        assertEquals("SentinelGridDemoHikariPool", environment.getProperty("spring.datasource.hikari.pool-name"),
                "Cloud demo profile must configure SentinelGridDemoHikariPool");
        assertEquals("5", environment.getProperty("spring.datasource.hikari.maximum-pool-size"),
                "Cloud demo profile must limit Hikari maximum pool size to 5 for Neon Free tier");
        assertEquals("1", environment.getProperty("spring.datasource.hikari.minimum-idle"),
                "Cloud demo profile must configure minimum-idle to 1");
    }

    @Test
    @DisplayName("Cloud demo profile: SQL script init mode is 'never' (Flyway authoritative)")
    void testSqlInitModeIsNeverInCloudDemo() {
        assertEquals("never", environment.getProperty("spring.sql.init.mode"),
                "Cloud demo profile must configure spring.sql.init.mode=never");
    }

    @Test
    @DisplayName("Cloud demo profile: /h2-console is blocked (403 Forbidden)")
    void testH2ConsoleBlockedInCloudDemo() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ProfileSafetyValidator accepts 'demo' and 'cloud-demo' profiles")
    void testProfileSafetyValidatorAcceptsCloudDemoProfiles() {
        MockEnvironment mockEnv1 = new MockEnvironment();
        mockEnv1.setActiveProfiles("demo");
        ProfileSafetyValidator validator1 = new ProfileSafetyValidator(mockEnv1);
        assertDoesNotThrow(validator1::validateProfileConfiguration, "ProfileSafetyValidator must accept 'demo'");

        MockEnvironment mockEnv2 = new MockEnvironment();
        mockEnv2.setActiveProfiles("cloud-demo");
        ProfileSafetyValidator validator2 = new ProfileSafetyValidator(mockEnv2);
        assertDoesNotThrow(validator2::validateProfileConfiguration, "ProfileSafetyValidator must accept 'cloud-demo'");
    }
}

