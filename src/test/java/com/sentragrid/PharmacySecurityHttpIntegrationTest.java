package com.sentragrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class PharmacySecurityHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("HTTP Security - Patient receives HTTP 403 Forbidden when requesting pharmacy inventory")
    void testPatientReceivesHttp403ForInventory() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/inventory")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP Security - Authorized pharmacist receives HTTP 200 for assigned pharmacy inventory")
    void testAuthorizedPharmacistReceivesHttp200ForAssignedBranchInventory() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/inventory")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HTTP Security - Pharmacist receives HTTP 403 Forbidden when requesting another pharmacy inventory")
    void testPharmacistReceivesHttp403ForDifferentBranchInventory() throws Exception {
        mockMvc.perform(get("/api/pharmacies/2/inventory")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP Security - Unauthenticated user receives HTTP 401/403 for pharmacy inventory")
    void testUnauthenticatedUserReceivesForbiddenOrUnauthorized() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/inventory"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("HTTP Security - Public medicine search is accessible without authentication (HTTP 200)")
    void testPublicMedicineSearchIsPermitted() throws Exception {
        mockMvc.perform(get("/api/medicines/search"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HTTP Security - Patient receives HTTP 403 Forbidden when requesting pharmacy reservations")
    void testPatientReceivesHttp403ForReservations() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/reservations")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP Security - Authorized pharmacist receives HTTP 200 for assigned pharmacy reservations")
    void testAuthorizedPharmacistReceivesHttp200ForAssignedBranchReservations() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/reservations")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HTTP Security - Pharmacist receives HTTP 403 Forbidden when requesting another pharmacy reservations")
    void testPharmacistReceivesHttp403ForDifferentBranchReservations() throws Exception {
        mockMvc.perform(get("/api/pharmacies/2/reservations")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HTTP Security - Admin receives HTTP 200 for any pharmacy reservations")
    void testAdminReceivesHttp200ForPharmacyReservations() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/reservations")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("HTTP Security - Authorized pharmacist can filter reservations by status")
    void testPharmacistFilterReservationsByStatus() throws Exception {
        mockMvc.perform(get("/api/pharmacies/1/reservations")
                .param("status", "PENDING")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RBAC Boundary - System observability is accessible strictly to ADMIN (HTTP 200)")
    void testSystemObservabilityAccessibleToAdmin() throws Exception {
        mockMvc.perform(get("/api/system/observability")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RBAC Boundary - System observability is forbidden to PHARMACY_MANAGER (HTTP 403)")
    void testSystemObservabilityForbiddenToManager() throws Exception {
        mockMvc.perform(get("/api/system/observability")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - System observability is forbidden to PHARMACIST (HTTP 403)")
    void testSystemObservabilityForbiddenToPharmacist() throws Exception {
        mockMvc.perform(get("/api/system/observability")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - System observability is forbidden to PATIENT (HTTP 403)")
    void testSystemObservabilityForbiddenToPatient() throws Exception {
        mockMvc.perform(get("/api/system/observability")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - Admin dashboard is forbidden to PHARMACIST (HTTP 403)")
    void testAdminDashboardForbiddenToPharmacist() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - Admin dashboard is forbidden to PHARMACY_MANAGER (HTTP 403)")
    void testAdminDashboardForbiddenToManager() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - Admin dashboard is forbidden to PATIENT (HTTP 403)")
    void testAdminDashboardForbiddenToPatient() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - Manager workspace is forbidden to PHARMACIST (HTTP 403)")
    void testManagerWorkspaceForbiddenToPharmacist() throws Exception {
        mockMvc.perform(get("/api/manager/workspace")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Boundary - Manager workspace is forbidden to PATIENT (HTTP 403)")
    void testManagerWorkspaceForbiddenToPatient() throws Exception {
        mockMvc.perform(get("/api/manager/workspace")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }
}
