package com.sentragrid.ai.dto;

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
    private String normalizedMedicine;
    private String category;
    private String explanation;
    private List<InventoryDto> searchResults;
}
