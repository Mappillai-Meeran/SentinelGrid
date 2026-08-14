package com.sentragrid.ai.controller;

import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.ai.service.GeminiSearchService;
import com.sentragrid.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI Search Assistant", description = "LLM-Powered Natural Language Medicine Search API")
public class AiSearchController {

    private final GeminiSearchService geminiSearchService;

    @PostMapping("/search-assist")
    @Operation(summary = "Convert natural language symptoms/queries into structured medicine search results using Gemini AI")
    public ResponseEntity<ApiResponse<SearchNormalizationResponse>> searchAssist(
            @Valid @RequestBody SearchNormalizationRequest request) {
        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);
        return ResponseEntity.ok(ApiResponse.success(response, "AI search query normalized successfully"));
    }
}
