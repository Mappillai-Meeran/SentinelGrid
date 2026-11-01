package com.sentragrid.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sentragrid.dto.InventoryDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchNormalizationResponse {

    private String originalPrompt;

    @JsonProperty("normalizedMedicine")
    @JsonAlias({"normalizedMedicine", "normalizedMedicineName"})
    private String normalizedMedicine;

    @JsonProperty("normalizedMedicineName")
    public String getNormalizedMedicineName() {
        return normalizedMedicine;
    }

    private String category;
    private String explanation;
    private List<InventoryDto> searchResults;

    @Builder.Default
    private boolean catalogMatch = false;

    @Builder.Default
    private boolean symptomQuery = false;
}
