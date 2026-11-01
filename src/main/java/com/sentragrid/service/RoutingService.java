package com.sentragrid.service;

import com.sentragrid.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class RoutingService {

    @Value("${app.routing.ors.api-key:}")
    private String apiKey;

    @Value("${app.routing.ors.base-url:https://api.heigit.org/openrouteservice/v2/matrix/driving-car}")
    private String orsUrl;

    private final RestTemplate restTemplate;

    public RoutingService() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public RoutingService(RestTemplate restTemplate, String apiKey, String orsUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.orsUrl = orsUrl;
    }

    public RoutingEstimateResponse estimateRoutes(RoutingEstimateRequest request) {
        List<DestinationCoordinateDto> validDestinations = request.getDestinations() != null
                ? request.getDestinations()
                : Collections.emptyList();

        List<RouteEstimateDto> resultRoutes = new ArrayList<>();

        if (request.getOrigin() == null || validDestinations.isEmpty()) {
            return new RoutingEstimateResponse(resultRoutes);
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("ORS API key is not configured. Returning UNAVAILABLE routing status.");
            return buildUnavailableResponse(validDestinations);
        }

        try {
            List<List<Double>> locations = new ArrayList<>();
            // ORS expects GeoJSON coordinate pairs: [longitude, latitude]
            locations.add(Arrays.asList(request.getOrigin().getLongitude(), request.getOrigin().getLatitude()));

            List<Integer> destIndices = new ArrayList<>();
            for (int i = 0; i < validDestinations.size(); i++) {
                DestinationCoordinateDto dest = validDestinations.get(i);
                locations.add(Arrays.asList(dest.getLongitude(), dest.getLatitude()));
                destIndices.add(i + 1);
            }

            Map<String, Object> orsRequestBody = new HashMap<>();
            orsRequestBody.put("locations", locations);
            orsRequestBody.put("sources", Collections.singletonList(0));
            orsRequestBody.put("destinations", destIndices);
            orsRequestBody.put("metrics", Arrays.asList("distance", "duration"));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", apiKey.trim());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(orsRequestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(orsUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                List<List<Object>> distances = (List<List<Object>>) body.get("distances");
                List<List<Object>> durations = (List<List<Object>>) body.get("durations");

                if (distances != null && !distances.isEmpty() && durations != null && !durations.isEmpty()) {
                    List<Object> distList = distances.get(0);
                    List<Object> durList = durations.get(0);

                    for (int i = 0; i < validDestinations.size(); i++) {
                        DestinationCoordinateDto dest = validDestinations.get(i);
                        Object distObj = i < distList.size() ? distList.get(i) : null;
                        Object durObj = i < durList.size() ? durList.get(i) : null;

                        if (distObj instanceof Number distNum && durObj instanceof Number durNum) {
                            double meters = distNum.doubleValue();
                            double seconds = durNum.doubleValue();

                            double distKm = Math.round((meters / 1000.0) * 10.0) / 10.0;
                            int durMins = Math.max(1, (int) Math.round(seconds / 60.0));

                            resultRoutes.add(RouteEstimateDto.builder()
                                    .pharmacyId(dest.getId())
                                    .distanceKm(distKm)
                                    .durationMinutes(durMins)
                                    .status("SUCCESS")
                                    .build());
                        } else {
                            resultRoutes.add(buildUnavailableRoute(dest.getId()));
                        }
                    }
                    return new RoutingEstimateResponse(resultRoutes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve routing estimates from ORS: {}", e.getMessage());
        }

        return buildUnavailableResponse(validDestinations);
    }

    private RoutingEstimateResponse buildUnavailableResponse(List<DestinationCoordinateDto> destinations) {
        List<RouteEstimateDto> routes = new ArrayList<>();
        for (DestinationCoordinateDto dest : destinations) {
            routes.add(buildUnavailableRoute(dest.getId()));
        }
        return new RoutingEstimateResponse(routes);
    }

    private RouteEstimateDto buildUnavailableRoute(Long pharmacyId) {
        return RouteEstimateDto.builder()
                .pharmacyId(pharmacyId)
                .distanceKm(null)
                .durationMinutes(null)
                .status("UNAVAILABLE")
                .build();
    }
}
