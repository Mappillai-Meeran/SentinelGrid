package com.sentragrid.system.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.system.dto.ObservabilityDto;
import com.sentragrid.system.dto.ObservabilityMetricDto;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemObservabilityController {

    private final MeterRegistry meterRegistry;
    private final HealthEndpoint healthEndpoint;

    @GetMapping("/observability")
    public ResponseEntity<ApiResponse<ObservabilityDto>> getObservability() {
        ObservabilityDto response = ObservabilityDto.builder()
                .healthStatus(healthEndpoint.health().getStatus().getCode())
                .metrics(List.of(
                        metric("JVM Memory Used", "jvm.memory.used", "bytes", "Actuator JVM memory telemetry"),
                        metric("JVM GC Pause Count", "jvm.gc.pause", "events", "Micrometer GC pause event count"),
                        metric("HTTP Server Requests", "http.server.requests", "requests", "Spring MVC request counter"),
                        staticMetric("Redis Cache Status", "Spring Cache active", "Cache annotations configured; hit-rate requires Redis cache statistics", "AVAILABLE"),
                        staticMetric("Kafka Event Pipeline", "Producer/Consumer configured", "Throughput counters require broker-side telemetry", "AVAILABLE")
                ))
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "System observability metrics retrieved"));
    }

    private ObservabilityMetricDto metric(String name, String meterName, String unit, String detail) {
        double value = 0;
        String status = "AVAILABLE";
        try {
            value = meterRegistry.get(meterName).meters().stream()
                    .flatMap(meter -> meter.measure().spliterator().hasCharacteristics(0)
                            ? java.util.stream.Stream.empty()
                            : java.util.stream.StreamSupport.stream(meter.measure().spliterator(), false))
                    .mapToDouble(measurement -> measurement.getValue())
                    .sum();
        } catch (Exception ex) {
            status = "UNAVAILABLE";
        }

        String formatted = status.equals("AVAILABLE") ? formatValue(value, unit) : "Metric unavailable";
        return ObservabilityMetricDto.builder()
                .name(name)
                .value(formatted)
                .detail(detail)
                .status(status)
                .build();
    }

    private ObservabilityMetricDto staticMetric(String name, String value, String detail, String status) {
        return ObservabilityMetricDto.builder()
                .name(name)
                .value(value)
                .detail(detail)
                .status(status)
                .build();
    }

    private String formatValue(double value, String unit) {
        if ("bytes".equals(unit)) {
            return String.format("%.1f MB", value / 1024 / 1024);
        }
        if ("events".equals(unit) || "requests".equals(unit)) {
            return String.format("%.0f %s", value, unit);
        }
        return String.format("%.2f %s", value, unit);
    }
}
