package com.sentragrid.service;

import com.sentragrid.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class RoutingServiceTest {

    private RestTemplate mockRestTemplate;
    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        mockRestTemplate = Mockito.mock(RestTemplate.class);
        routingService = new RoutingService(mockRestTemplate, "test-ors-api-key", "https://api.heigit.org/openrouteservice/v2/matrix/driving-car");
    }

    @Test
    @DisplayName("TEST 1: Valid origin and multiple destinations with successful ORS response")
    void testSuccessfulRoutingEstimate() {
        CoordinateDto origin = new CoordinateDto(11.9356, 79.8337);
        DestinationCoordinateDto dest1 = new DestinationCoordinateDto(1L, 11.9324, 79.8308);
        DestinationCoordinateDto dest2 = new DestinationCoordinateDto(2L, 11.9391, 79.8056);

        RoutingEstimateRequest request = new RoutingEstimateRequest(origin, Arrays.asList(dest1, dest2));

        Map<String, Object> mockResponseBody = new HashMap<>();
        mockResponseBody.put("distances", Arrays.asList(Arrays.asList(1500.0, 4200.0)));
        mockResponseBody.put("durations", Arrays.asList(Arrays.asList(300.0, 720.0)));

        ResponseEntity<Map> mockEntity = new ResponseEntity<>(mockResponseBody, HttpStatus.OK);

        Mockito.when(mockRestTemplate.exchange(
                eq("https://api.heigit.org/openrouteservice/v2/matrix/driving-car"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(mockEntity);

        RoutingEstimateResponse response = routingService.estimateRoutes(request);

        assertNotNull(response);
        assertEquals(2, response.getRoutes().size());

        RouteEstimateDto route1 = response.getRoutes().get(0);
        assertEquals(1L, route1.getPharmacyId());
        assertEquals(1.5, route1.getDistanceKm());
        assertEquals(5, route1.getDurationMinutes());
        assertEquals("SUCCESS", route1.getStatus());

        RouteEstimateDto route2 = response.getRoutes().get(1);
        assertEquals(2L, route2.getPharmacyId());
        assertEquals(4.2, route2.getDistanceKm());
        assertEquals(12, route2.getDurationMinutes());
        assertEquals("SUCCESS", route2.getStatus());
    }

    @Test
    @DisplayName("TEST 2: Missing ORS API key returns safe UNAVAILABLE response without crash")
    void testMissingApiKey() {
        RoutingService noKeyService = new RoutingService(mockRestTemplate, "", "https://api.heigit.org/openrouteservice/v2/matrix/driving-car");

        CoordinateDto origin = new CoordinateDto(11.9356, 79.8337);
        DestinationCoordinateDto dest1 = new DestinationCoordinateDto(1L, 11.9324, 79.8308);
        RoutingEstimateRequest request = new RoutingEstimateRequest(origin, Collections.singletonList(dest1));

        RoutingEstimateResponse response = noKeyService.estimateRoutes(request);

        assertNotNull(response);
        assertEquals(1, response.getRoutes().size());
        assertEquals(1L, response.getRoutes().get(0).getPharmacyId());
        assertNull(response.getRoutes().get(0).getDistanceKm());
        assertNull(response.getRoutes().get(0).getDurationMinutes());
        assertEquals("UNAVAILABLE", response.getRoutes().get(0).getStatus());
    }

    @Test
    @DisplayName("TEST 3: ORS network failure / rate limit (429) returns safe UNAVAILABLE response")
    void testOrsNetworkFailureOrRateLimit() {
        CoordinateDto origin = new CoordinateDto(11.9356, 79.8337);
        DestinationCoordinateDto dest1 = new DestinationCoordinateDto(1L, 11.9324, 79.8308);
        RoutingEstimateRequest request = new RoutingEstimateRequest(origin, Collections.singletonList(dest1));

        Mockito.when(mockRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));

        RoutingEstimateResponse response = routingService.estimateRoutes(request);

        assertNotNull(response);
        assertEquals(1, response.getRoutes().size());
        assertEquals("UNAVAILABLE", response.getRoutes().get(0).getStatus());
    }
}
