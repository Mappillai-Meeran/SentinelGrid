package com.sentragrid.dashboard.controller;

import com.sentragrid.dashboard.dto.DashboardSummaryDto;
import com.sentragrid.dashboard.dto.TopPharmacyDto;
import com.sentragrid.dashboard.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    @DisplayName("Admin Authorization - Admin receives HTTP 200 and serialized TopPharmacyDto with fulfillment rate")
    void testGetTopPharmacies_AdminAllowedWithFulfillmentRate() throws Exception {
        List<TopPharmacyDto> mockDtos = List.of(
                new TopPharmacyDto("Apollo M.G. Road", 25L, 18L, 20L, 90.0),
                new TopPharmacyDto("New Branch", 5L, 0L, 0L, null)
        );

        when(dashboardService.getTopPharmacies(anyInt())).thenReturn(mockDtos);

        mockMvc.perform(get("/api/admin/dashboard/pharmacies/top?limit=5")
                .with(user("admin1").roles("ADMIN"))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].pharmacyName").value("Apollo M.G. Road"))
                .andExpect(jsonPath("$.data[0].reservationCount").value(25))
                .andExpect(jsonPath("$.data[0].completedCount").value(18))
                .andExpect(jsonPath("$.data[0].concludedCount").value(20))
                .andExpect(jsonPath("$.data[0].fulfillmentRate").value(90.0))
                .andExpect(jsonPath("$.data[1].pharmacyName").value("New Branch"))
                .andExpect(jsonPath("$.data[1].fulfillmentRate").value(nullValue()));
    }

    @Test
    @DisplayName("RBAC Protection - Patient is denied (HTTP 403) from accessing admin top pharmacies API")
    void testGetTopPharmacies_PatientForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/pharmacies/top?limit=5")
                .with(user("patient1").roles("PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Protection - Pharmacist is denied (HTTP 403) from accessing admin top pharmacies API")
    void testGetTopPharmacies_PharmacistForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/pharmacies/top?limit=5")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RBAC Protection - Pharmacy Manager is denied (HTTP 403) from accessing admin top pharmacies API")
    void testGetTopPharmacies_PharmacyManagerForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/pharmacies/top?limit=5")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin Summary - Returns 24/7 and Standard Hours breakdown in DashboardSummaryDto")
    void testGetSummaryMetrics_IncludesBranchOperatingBreakdown() throws Exception {
        DashboardSummaryDto mockSummary = DashboardSummaryDto.builder()
                .totalPharmacies(15L)
                .twentyFourSevenPharmacies(3L)
                .standardHoursPharmacies(12L)
                .totalUsers(40L)
                .totalMedicines(10L)
                .totalReservations(50L)
                .pendingReservations(5L)
                .confirmedReservations(35L)
                .expiredReservations(7L)
                .cancelledReservations(3L)
                .totalInventoryUnits(420L)
                .build();

        when(dashboardService.getSummaryMetrics()).thenReturn(mockSummary);

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(user("admin1").roles("ADMIN"))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPharmacies").value(15))
                .andExpect(jsonPath("$.data.twentyFourSevenPharmacies").value(3))
                .andExpect(jsonPath("$.data.standardHoursPharmacies").value(12))
                .andExpect(jsonPath("$.data.totalInventoryUnits").value(420));
    }
}

