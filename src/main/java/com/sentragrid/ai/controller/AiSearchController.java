package com.sentragrid.ai.controller;

import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.ai.service.GeminiSearchService;
import com.sentragrid.common.ApiResponse;
import com.sentragrid.security.ClientIpResolver;
import com.sentragrid.security.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/search-assist")
    @Operation(summary = "Clarify and normalize medicine search queries against the emergency medicine catalog")
    public ResponseEntity<ApiResponse<SearchNormalizationResponse>> searchAssist(
            @Valid @RequestBody SearchNormalizationRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.extractClientIp(httpRequest);
        rateLimiterService.checkAiSearchAllowed(clientIp);

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);
        rateLimiterService.recordAiSearch(clientIp);

        return ResponseEntity.ok(ApiResponse.success(response, "AI search query normalized successfully"));
    }
}
