package com.sentragrid.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchNormalizationRequest {

    @NotBlank(message = "Natural language prompt cannot be blank")
    private String prompt;
    
    private String city;
}
