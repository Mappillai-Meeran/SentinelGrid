package com.sentragrid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.ai.service.GeminiSearchService;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.PharmacyDto;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MedicineSearchReliabilityAndSafetyTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private MedicineRepository medicineRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MedicineService medicineService;

    private GeminiSearchService geminiSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Medicine coviforMed;
    private Medicine actemraMed;
    private Pharmacy apolloPharmacy;
    private Inventory coviforInventory;

    @BeforeEach
    void setUp() {
        coviforMed = Medicine.builder()
                .id(1L)
                .name("Remdesivir 100mg Injection")
                .brand("Covifor")
                .category("ANTIVIRAL")
                .description("Antiviral medication")
                .requiresPrescription(true)
                .build();

        actemraMed = Medicine.builder()
                .id(2L)
                .name("Tocilizumab 400mg")
                .brand("Actemra")
                .category("IMMUNOSUPPRESSANT")
                .description("Immunosuppressive drug")
                .requiresPrescription(true)
                .build();

        apolloPharmacy = Pharmacy.builder()
                .id(1L)
                .name("Apollo Pharmacy M.G. Road")
                .city("Puducherry")
                .is24Seven(true)
                .build();

        coviforInventory = Inventory.builder()
                .id(101L)
                .pharmacy(apolloPharmacy)
                .medicine(coviforMed)
                .quantity(10)
                .reservedQuantity(2)
                .build();

        geminiSearchService = new GeminiSearchService(medicineService, medicineRepository, objectMapper);
        geminiSearchService.setRestTemplate(restTemplate);
    }

    // ==========================================
    // 1. DATABASE-BACKED SEARCH RELIABILITY TESTS
    // ==========================================

    @Test
    @DisplayName("Search by brand name 'Covifor' returns verified database inventory")
    void testSearchByBrandName() {
        when(inventoryRepository.searchAvailableInventory(eq("Covifor"), eq("Puducherry")))
                .thenReturn(Collections.singletonList(coviforInventory));

        List<InventoryDto> results = medicineService.searchMedicines("Covifor", "Puducherry");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Remdesivir 100mg Injection", results.get(0).getMedicine().getName());
        assertEquals("Covifor", results.get(0).getMedicine().getBrand());
        assertEquals(8, results.get(0).getAvailableQuantity());
        verify(inventoryRepository, times(1)).searchAvailableInventory("Covifor", "Puducherry");
    }

    @Test
    @DisplayName("Search with whitespace-only is sanitized to unconstrained search")
    void testSearchWhitespaceSanitization() {
        when(inventoryRepository.searchAvailableInventory(isNull(), eq("Puducherry")))
                .thenReturn(Collections.singletonList(coviforInventory));

        List<InventoryDto> results = medicineService.searchMedicines("   ", "Puducherry");

        assertEquals(1, results.size());
        // Verify null was passed down to repository instead of empty/whitespace
        verify(inventoryRepository, times(1)).searchAvailableInventory(isNull(), eq("Puducherry"));
    }

    @Test
    @DisplayName("Search with no matching database record returns empty list deterministically")
    void testSearchNoMatches() {
        when(inventoryRepository.searchAvailableInventory(eq("NonExistentMed"), eq("Puducherry")))
                .thenReturn(Collections.emptyList());

        List<InventoryDto> results = medicineService.searchMedicines("NonExistentMed", "Puducherry");

        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(inventoryRepository, times(1)).searchAvailableInventory("NonExistentMed", "Puducherry");
    }

    // ==========================================
    // 2. AI SAFETY & SYMPTOM GUARDRAIL TESTS
    // ==========================================

    @Test
    @DisplayName("Symptom queries must NOT produce a medicine recommendation or diagnosis")
    void testSymptomQueryRefusesMedicineRecommendation() {
        when(medicineRepository.searchByNameOrBrand(anyString())).thenReturn(Collections.emptyList());

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("severe fever, cough and chest pain")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isSymptomQuery(), "Must flag symptom query");
        assertFalse(response.isCatalogMatch(), "Must not claim a catalog match for symptoms");
        assertNull(response.getNormalizedMedicine(), "Must NOT recommend a medicine for symptoms");
        assertTrue(response.getExplanation().contains("does not provide medical diagnosis"),
                "Must provide clinical safety advice");
        assertTrue(response.getSearchResults().isEmpty(), "Must return empty search results for symptom queries");
    }

    @Test
    @DisplayName("Unverified / hallucinated medicine from AI is rejected and not presented as verified catalog result")
    void testUnverifiedAiMedicineNameIsRejected() throws Exception {
        ReflectionTestUtils.setField(geminiSearchService, "apiKey", "test-api-key");

        // Mock Gemini returning a fabricated medicine name not in our database
        String geminiJson = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"{\\\"normalizedMedicine\\\": \\\"MiracleCure 9000\\\", \\\"category\\\": \\\"ANTIVIRAL\\\", \\\"isSymptomQuery\\\": false}\"}]}}]}";
        ResponseEntity<String> mockResp = new ResponseEntity<>(geminiJson, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class))).thenReturn(mockResp);

        // Database has NO record for "MiracleCure 9000"
        when(medicineRepository.searchByNameOrBrand(eq("MiracleCure 9000"))).thenReturn(Collections.emptyList());

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("MiracleCure")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertFalse(response.isCatalogMatch(), "Must NOT claim a catalog match for hallucinated medicine");
        assertNull(response.getNormalizedMedicine(), "Fabricated medicine name must be stripped from response");
        assertTrue(response.getSearchResults().isEmpty(), "No fabricated inventory can be returned");
        assertTrue(response.getExplanation().contains("No matching medicine found in SentinelGrid's catalog"));
    }

    // ==========================================
    // 3. PROVIDER RESILIENCE & TIMEOUT TESTS
    // ==========================================

    @Test
    @DisplayName("Missing API key uses local deterministic catalog match without error")
    void testMissingApiKeyFallsBackToLocalCatalog() {
        ReflectionTestUtils.setField(geminiSearchService, "apiKey", "");

        when(medicineRepository.searchByNameOrBrand(eq("Covifor")))
                .thenReturn(Collections.singletonList(coviforMed));
        when(inventoryRepository.searchAvailableInventory(eq("Remdesivir 100mg Injection"), eq("Puducherry")))
                .thenReturn(Collections.singletonList(coviforInventory));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Covifor")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isCatalogMatch());
        assertEquals("Remdesivir 100mg Injection", response.getNormalizedMedicine());
        assertEquals(1, response.getSearchResults().size());
        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    @DisplayName("Gemini API connection/read timeout (3s) falls back gracefully without breaking request")
    void testGeminiApiTimeoutFallback() {
        ReflectionTestUtils.setField(geminiSearchService, "apiKey", "test-api-key");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out after 3000ms"));

        when(medicineRepository.searchByNameOrBrand(eq("Actemra")))
                .thenReturn(Collections.singletonList(actemraMed));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Actemra")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isCatalogMatch());
        assertEquals("Tocilizumab 400mg", response.getNormalizedMedicine());
    }

    @Test
    @DisplayName("Gemini HTTP 429 quota exhaustion falls back gracefully")
    void testGeminiQuotaExhaustionFallback() {
        ReflectionTestUtils.setField(geminiSearchService, "apiKey", "test-api-key");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS, "Quota exceeded"));

        when(medicineRepository.searchByNameOrBrand(eq("Covifor")))
                .thenReturn(Collections.singletonList(coviforMed));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Covifor")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isCatalogMatch());
        assertEquals("Remdesivir 100mg Injection", response.getNormalizedMedicine());
    }

    @Test
    @DisplayName("Gemini empty candidates / safety-blocked response handled without NPE")
    void testGeminiSafetyBlockedResponseHandledWithoutNpe() {
        ReflectionTestUtils.setField(geminiSearchService, "apiKey", "test-api-key");

        String safetyBlockedJson = "{\"candidates\": []}";
        ResponseEntity<String> mockResp = new ResponseEntity<>(safetyBlockedJson, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class))).thenReturn(mockResp);

        when(medicineRepository.searchByNameOrBrand(eq("Remdesivir")))
                .thenReturn(Collections.singletonList(coviforMed));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Remdesivir")
                .city("Puducherry")
                .build();

        assertDoesNotThrow(() -> {
            SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);
            assertNotNull(response);
            assertTrue(response.isCatalogMatch());
            assertEquals("Remdesivir 100mg Injection", response.getNormalizedMedicine());
        });
    }

    @Test
    @DisplayName("Gemini malformed non-JSON response handled without NPE")
    void testGeminiMalformedJsonHandledWithoutNpe() {
        ReflectionTestUtils.setField(geminiSearchService, "apiKey", "test-api-key");

        String malformedJson = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"THIS IS NOT VALID JSON\"}]}}]}";
        ResponseEntity<String> mockResp = new ResponseEntity<>(malformedJson, HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class))).thenReturn(mockResp);

        when(medicineRepository.searchByNameOrBrand(eq("Remdesivir")))
                .thenReturn(Collections.singletonList(coviforMed));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Remdesivir")
                .city("Puducherry")
                .build();

        assertDoesNotThrow(() -> {
            SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);
            assertNotNull(response);
            assertTrue(response.isCatalogMatch());
            assertEquals("Remdesivir 100mg Injection", response.getNormalizedMedicine());
        });
    }

    // ==========================================
    // 4. STEP 3.2 TARGETED CORRECTIONS TESTS
    // ==========================================

    @Test
    @DisplayName("SG-SRCH-01: Exact name/brand match is preferred over partial match")
    void testExactMatchPreferredOverSubstring() {
        when(medicineRepository.findExactByNameOrBrand(eq("Covifor")))
                .thenReturn(Collections.singletonList(coviforMed));
        when(inventoryRepository.searchAvailableInventory(eq("Remdesivir 100mg Injection"), eq("Puducherry")))
                .thenReturn(Collections.singletonList(coviforInventory));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Covifor")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isCatalogMatch());
        assertEquals("Remdesivir 100mg Injection", response.getNormalizedMedicine());
        verify(medicineRepository, times(1)).findExactByNameOrBrand("Covifor");
        verify(medicineRepository, never()).searchByNameOrBrand("Covifor");
    }

    @Test
    @DisplayName("SG-SRCH-01: Ambiguous partial match across distinct medicines is not arbitrarily picked")
    void testAmbiguousPartialMatchesFlagged() {
        Medicine covifor200 = Medicine.builder()
                .id(3L)
                .name("Remdesivir 200mg Injection")
                .brand("Covifor")
                .category("ANTIVIRAL")
                .build();

        when(medicineRepository.findExactByNameOrBrand(eq("Remdesivir"))).thenReturn(Collections.emptyList());
        when(medicineRepository.searchByNameOrBrand(eq("Remdesivir"))).thenReturn(Arrays.asList(coviforMed, covifor200));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Remdesivir")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertFalse(response.isCatalogMatch(), "Must NOT claim a match when query is ambiguous");
        assertNull(response.getNormalizedMedicine());
        assertTrue(response.getExplanation().contains("Ambiguous query"), "Must explain ambiguity to user");
        assertTrue(response.getSearchResults().isEmpty());
    }

    @Test
    @DisplayName("SG-SRCH-01: Ambiguous exact match across multiple formulations is flagged without picking get(0)")
    void testAmbiguousExactMatchesFlagged() {
        Medicine covifor100 = Medicine.builder().id(1L).name("Remdesivir 100mg Injection").brand("Covifor").build();
        Medicine covifor200 = Medicine.builder().id(2L).name("Remdesivir 200mg Injection").brand("Covifor").build();

        when(medicineRepository.findExactByNameOrBrand(eq("Covifor"))).thenReturn(Arrays.asList(covifor100, covifor200));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Covifor")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertFalse(response.isCatalogMatch());
        assertNull(response.getNormalizedMedicine());
        assertTrue(response.getExplanation().contains("Multiple distinct catalog medicines found for exact term"));
        assertTrue(response.getSearchResults().isEmpty());
    }

    @Test
    @DisplayName("SG-SRCH-02: Compound query 'Anti-Venom for snake bite' resolves to medicine with safety disclaimer")
    void testCompoundQueryResolvesMedicineWithDisclaimer() {
        Medicine antiVenomMed = Medicine.builder()
                .id(6L)
                .name("Anti-Venom Polyvalent Injection")
                .brand("VenoCure")
                .category("EMERGENCY_ANTIVENOM")
                .build();

        when(medicineRepository.findAll()).thenReturn(Collections.singletonList(antiVenomMed));
        when(inventoryRepository.searchAvailableInventory(eq("Anti-Venom Polyvalent Injection"), eq("Puducherry")))
                .thenReturn(Collections.emptyList());

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Anti-Venom for snake bite")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isCatalogMatch(), "Must match catalog medicine in compound query");
        assertFalse(response.isSymptomQuery(), "Must not block search when specific medicine is requested");
        assertEquals("Anti-Venom Polyvalent Injection", response.getNormalizedMedicine());
        assertTrue(response.getExplanation().contains("does not verify medical appropriateness for specific symptoms"),
                "Must attach safety disclaimer regarding symptom appropriateness");
    }

    @Test
    @DisplayName("Natural language emergency query 'snake bite' resolves to Anti-Venom Polyvalent Injection")
    void testNaturalLanguageSnakeBiteResolvesToAntiVenom() {
        Medicine antiVenomMed = Medicine.builder()
                .id(6L)
                .name("Anti-Venom Polyvalent Injection")
                .brand("VenoCure")
                .category("EMERGENCY_ANTIVENOM")
                .build();

        when(medicineRepository.searchByNameOrBrand(anyString())).thenAnswer(inv -> {
            String arg = inv.getArgument(0);
            return "Anti-Venom Polyvalent Injection".equalsIgnoreCase(arg) ? Collections.singletonList(antiVenomMed) : Collections.emptyList();
        });
        when(medicineRepository.findExactByNameOrBrand(anyString())).thenAnswer(inv -> {
            String arg = inv.getArgument(0);
            return "Anti-Venom Polyvalent Injection".equalsIgnoreCase(arg) ? Collections.singletonList(antiVenomMed) : Collections.emptyList();
        });
        when(inventoryRepository.searchAvailableInventory(eq("Anti-Venom Polyvalent Injection"), eq("Puducherry")))
                .thenReturn(Collections.emptyList());

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("snake bite")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isCatalogMatch(), "Must match Anti-Venom catalog medicine");
        assertFalse(response.isSymptomQuery(), "Must not block emergency antivenom resolution");
        assertEquals("Anti-Venom Polyvalent Injection", response.getNormalizedMedicine());
        assertEquals("EMERGENCY_ANTIVENOM", response.getCategory());
    }

    @Test
    @DisplayName("SG-SRCH-02: Compound query 'UnknownMedX for fever' strictly refuses recommendation")
    void testCompoundQueryWithUnknownMedicineRefusesRecommendation() {
        when(medicineRepository.findAll()).thenReturn(Collections.singletonList(coviforMed));
        when(medicineRepository.findExactByNameOrBrand(anyString())).thenReturn(Collections.emptyList());
        when(medicineRepository.searchByNameOrBrand(anyString())).thenReturn(Collections.emptyList());

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("UnknownMedX for fever")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isSymptomQuery(), "Must flag symptom query");
        assertFalse(response.isCatalogMatch());
        assertNull(response.getNormalizedMedicine());
        assertTrue(response.getExplanation().contains("Symptom description detected"));
        assertTrue(response.getSearchResults().isEmpty());
    }

    @Test
    @DisplayName("SG-SRCH-02: Pure symptom query 'What should I take for fever?' strictly refuses recommendation")
    void testPureSymptomQueryRefusesRecommendation() {
        when(medicineRepository.findAll()).thenReturn(Arrays.asList(coviforMed, actemraMed));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("What should I take for fever?")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertTrue(response.isSymptomQuery());
        assertFalse(response.isCatalogMatch());
        assertNull(response.getNormalizedMedicine());
        assertTrue(response.getSearchResults().isEmpty());
    }

    @Test
    @DisplayName("SG-SRCH-02: Compound query with multiple medicines asks for clarification")
    void testCompoundQueryWithMultipleMedicinesAsksClarification() {
        when(medicineRepository.findAll()).thenReturn(Arrays.asList(coviforMed, actemraMed));

        SearchNormalizationRequest request = SearchNormalizationRequest.builder()
                .prompt("Covifor or Actemra for severe fever")
                .city("Puducherry")
                .build();

        SearchNormalizationResponse response = geminiSearchService.normalizeAndSearch(request);

        assertNotNull(response);
        assertFalse(response.isCatalogMatch());
        assertNull(response.getNormalizedMedicine());
        assertTrue(response.getExplanation().contains("Multiple catalog medicines detected"));
        assertTrue(response.getSearchResults().isEmpty());
    }

    @Test
    @DisplayName("SG-SRCH-03: Rate limiter enforces thread-safe fixed window limit under concurrent load")
    void testConcurrentRateLimiterThreadSafety() throws InterruptedException {
        geminiSearchService.resetRateLimiterForTesting();

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean exceeded = geminiSearchService.isRateLimitExceeded();
                    if (exceeded) {
                        rejectedCount.incrementAndGet();
                    } else {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within 5 seconds");
        assertEquals(60, allowedCount.get(), "Exactly 60 requests must be allowed in a 1-minute window");
        assertEquals(40, rejectedCount.get(), "Exactly 40 requests must be rate-limited/rejected");
    }

    @Test
    @DisplayName("SG-SRCH-03: Rate limiter reset helper restores quota immediately")
    void testResetRateLimiter() {
        geminiSearchService.resetRateLimiterForTesting();

        for (int i = 0; i < 60; i++) {
            assertFalse(geminiSearchService.isRateLimitExceeded(), "Request " + (i + 1) + " should be allowed");
        }
        assertTrue(geminiSearchService.isRateLimitExceeded(), "Request 61 must be rejected");

        geminiSearchService.resetRateLimiterForTesting();
        assertFalse(geminiSearchService.isRateLimitExceeded(), "First request after reset must be allowed");
    }
}
