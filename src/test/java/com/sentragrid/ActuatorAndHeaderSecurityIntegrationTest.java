package com.sentragrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("Step 5D.1R - Actuator Access Control & Security Headers Integration Tests")
public class ActuatorAndHeaderSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("SEC-02/SEC-03: Security headers include SAMEORIGIN, nosniff, and Referrer-Policy")
    void testSecurityHeadersPresent() throws Exception {
        mockMvc.perform(get("/api/medicines/search"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    @DisplayName("SEC-04: Unauthenticated health check returns HTTP 200 with minimum status UP (no components)")
    void testUnauthenticatedHealthCheckReturnsMinimalStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("SEC-04: Unauthenticated metrics access is blocked (HTTP 401/403)")
    void testUnauthenticatedMetricsBlocked() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("SEC-04: PATIENT role cannot access metrics (HTTP 403 Forbidden)")
    void testPatientCannotAccessMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-04: PHARMACIST role cannot access metrics (HTTP 403 Forbidden)")
    void testPharmacistCannotAccessMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-04: PHARMACY_MANAGER role cannot access metrics (HTTP 403 Forbidden)")
    void testPharmacyManagerCannotAccessMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-04: ADMIN role can access metrics (HTTP 200 OK)")
    void testAdminCanAccessMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @DisplayName("SEC-04: PATIENT role health check only receives minimal status UP (no component details)")
    void testPatientHealthCheckReceivesMinimalStatus() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("SEC-04: ADMIN role health check receives detailed component health")
    void testAdminHealthCheckReceivesDetailedComponents() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").exists());
    }
}

