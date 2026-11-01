package com.sentragrid.service;

import com.sentragrid.config.DemoDataInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sentinelgrid",
        "spring.datasource.username=postgres",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:}",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "app.jwt.secret=ProductionTestSecretKeyMustBeAtLeast32BytesLongForHmacSha256!"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DisplayName("Step 5B.1 / 5B.2 - Production Startup Safety Context Test")
class ProductionStartupProfileSafetyTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private SqlInitializationProperties sqlInitializationProperties;

    @Test
    @DisplayName("Production profile: DemoDataInitializer bean is NOT created in Spring context")
    void testDemoDataInitializerAbsentInProduction() {
        assertFalse(applicationContext.containsBean("demoDataInitializer"),
                "DemoDataInitializer must NOT be present in production application context");
    }

    @Test
    @DisplayName("Production profile: spring.sql.init.mode is never and no SQL script database initializer is active")
    void testSqlInitializationDisabledInProduction() {
        assertEquals("never", environment.getProperty("spring.sql.init.mode"),
                "Production environment must resolve spring.sql.init.mode=never");

        if (sqlInitializationProperties != null) {
            assertEquals(DatabaseInitializationMode.NEVER, sqlInitializationProperties.getMode(),
                    "SqlInitializationProperties must resolve mode to NEVER in production");
        }

        assertFalse(applicationContext.containsBean("dataSourceScriptDatabaseInitializer"),
                "dataSourceScriptDatabaseInitializer bean must NOT be registered in production context");
    }

    @Test
    @DisplayName("Production profile: /h2-console is NOT accessible unauthenticated (401/403 Forbidden)")
    void testH2ConsoleBlockedInProduction() throws Exception {
        mockMvc.perform(get("/h2-console"))
                .andExpect(status().isForbidden());
    }
}
