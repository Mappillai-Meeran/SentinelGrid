package com.sentragrid.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingEstimateRequest {

    @NotNull(message = "Origin coordinate is required")
    @Valid
    private CoordinateDto origin;

    @NotEmpty(message = "Destinations list must not be empty")
    @Size(max = 25, message = "Destinations list must not exceed 25 items")
    @Valid
    private List<DestinationCoordinateDto> destinations;
}
