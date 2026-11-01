package com.sentragrid.security;

import com.sentragrid.ai.controller.AiSearchController;
import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.ai.service.GeminiSearchService;
import com.sentragrid.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 5C - AI Search Rate Limiting and Client IP Extraction Tests")
class AiSearchRateLimitingAndClientIpTest {

    @Mock
    private GeminiSearchService geminiSearchService;

    private RateLimiterService rateLimiterService;
    private ClientIpResolver clientIpResolver;
    private AiSearchController aiSearchController;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        clientIpResolver = new ClientIpResolver();
        aiSearchController = new AiSearchController(geminiSearchService, rateLimiterService, clientIpResolver);
    }

    @Test
    @DisplayName("AI search rate limiting: Permits up to 15 requests per minute from a single client IP")
    void testAiSearchAllowsUpTo15Requests() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.25");

        when(geminiSearchService.normalizeAndSearch(any()))
                .thenReturn(SearchNormalizationResponse.builder().normalizedMedicine("Remdesivir").build());

        SearchNormalizationRequest searchRequest = new SearchNormalizationRequest("remdesivir injection", "Puducherry");

        for (int i = 0; i < 15; i++) {
            assertDoesNotThrow(() -> aiSearchController.searchAssist(searchRequest, request),
                    "Request " + (i + 1) + " within 15 req/min threshold must succeed");
        }

        verify(geminiSearchService, times(15)).normalizeAndSearch(any());
    }

    @Test
    @DisplayName("AI search rate limiting: 16th request within window throws RateLimitExceededException (429)")
    void testAiSearchBlocks16thRequestWithRateLimitExceeded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.50");

        when(geminiSearchService.normalizeAndSearch(any()))
                .thenReturn(SearchNormalizationResponse.builder().normalizedMedicine("Remdesivir").build());

        SearchNormalizationRequest searchRequest = new SearchNormalizationRequest("remdesivir injection", "Puducherry");

        for (int i = 0; i < 15; i++) {
            aiSearchController.searchAssist(searchRequest, request);
        }

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> aiSearchController.searchAssist(searchRequest, request),
                "16th request must breach rate limit and throw RateLimitExceededException");

        assertTrue(ex.getMessage().contains("AI search rate limit exceeded"),
                "Exception message must specify AI search rate limit exceeded");
        assertTrue(ex.getRetryAfterSeconds() > 0, "Retry-After must be greater than zero");

        // Gemini search must NOT have been called for the 16th blocked request
        verify(geminiSearchService, times(15)).normalizeAndSearch(any());
    }

    @Test
    @DisplayName("AI search rate limiting: Different IP addresses have independent rate limit buckets")
    void testAiSearchDifferentIpsAreIsolated() {
        MockHttpServletRequest request1 = new MockHttpServletRequest();
        request1.setRemoteAddr("198.51.100.101");

        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr("198.51.100.102");

        when(geminiSearchService.normalizeAndSearch(any()))
                .thenReturn(SearchNormalizationResponse.builder().normalizedMedicine("Favipiravir").build());

        SearchNormalizationRequest searchRequest = new SearchNormalizationRequest("favipiravir", "Chennai");

        for (int i = 0; i < 15; i++) {
            aiSearchController.searchAssist(searchRequest, request1);
        }

        // IP 1 is now rate limited
        assertThrows(RateLimitExceededException.class, () -> aiSearchController.searchAssist(searchRequest, request1));

        // IP 2 must still succeed
        assertDoesNotThrow(() -> aiSearchController.searchAssist(searchRequest, request2),
                "Independent IP address must not be affected by another client's rate limit");
    }

    @Test
    @DisplayName("ClientIpResolver: Untrusted upstream proxy ignores X-Forwarded-For header")
    void testClientIpResolverIgnoresForwardedFromUntrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.195"); // Untrusted direct connection
        request.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.1");

        String resolvedIp = clientIpResolver.extractClientIp(request);
        assertEquals("203.0.113.195", resolvedIp, "Must ignore X-Forwarded-For when direct remoteAddr is not a trusted proxy");
    }

    @Test
    @DisplayName("ClientIpResolver: Trusted proxy extracts genuine client IP from right-to-left")
    void testClientIpResolverExtractsClientIpFromTrustedProxy() {
        clientIpResolver.configureTrustedProxies("10.0.0.0/8, 172.16.0.0/12");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5"); // Trusted proxy
        request.addHeader("X-Forwarded-For", "203.0.113.44, 10.0.0.2");

        String resolvedIp = clientIpResolver.extractClientIp(request);
        assertEquals("203.0.113.44", resolvedIp, "Must resolve genuine client IP preceding upstream internal proxy");
    }
}
