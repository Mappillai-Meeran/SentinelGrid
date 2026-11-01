package com.sentragrid.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 200, message = "Prompt cannot exceed 200 characters")
    @JsonAlias({"query", "prompt"})
    private String prompt;
    
    private String city;
}
