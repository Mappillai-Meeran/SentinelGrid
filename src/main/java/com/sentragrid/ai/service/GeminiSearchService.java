package com.sentragrid.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.entity.Medicine;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.service.MedicineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI Medicine Search Assistant Service.
 * Provides query terminology clarification and catalog resolution.
 * Strictly avoids medical diagnosis, treatment suggestions, or medicine invention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiSearchService {

    private final MedicineService medicineService;
    private final MedicineRepository medicineRepository;
    private final ObjectMapper objectMapper;

    private RestTemplate restTemplate;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String apiUrl;

    /**
     * Bounded fixed-window rate limiter for upstream Gemini API calls.
     *
     * SCOPE AND LIMITATIONS:
     * - This limiter is strictly GLOBAL per JVM application instance.
     * - It is NOT a distributed limiter and does NOT track per-user or per-IP quotas.
     * - In a horizontally-scaled multi-instance deployment, each node enforces its own
     *   local limit independently. For strict cross-cluster rate limiting, an API Gateway
     *   (e.g., Kong, Envoy) or a distributed Redis token-bucket should be deployed.
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private int requestCounter = 0;
    private long windowStartTime = System.currentTimeMillis();
    private final Object rateLimitLock = new Object();

    private static final Set<String> SYMPTOM_KEYWORDS = Set.of(
            "fever", "headache", "chest pain", "cough", "vomit", "vomiting", "nausea",
            "rash", "wound", "bleeding", "dizzy", "dizziness", "infection", "illness",
            "diarrhea", "stomach pain", "stomach ache", "throat pain", "body pain",
            "breathlessness", "shortness of breath", "symptom", "symptoms", "sick",
            "cold", "flu", "bite", "snake bite"
    );

    @Autowired(required = false)
    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private RestTemplate getRestTemplate() {
        if (this.restTemplate != null) {
            return this.restTemplate;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
        return this.restTemplate;
    }

    public static class CatalogResolutionResult {
        private final String resolvedMedicineName;
        private final String category;
        private final boolean match;
        private final boolean ambiguous;
        private final String explanation;

        public CatalogResolutionResult(String resolvedMedicineName, String category, boolean match, boolean ambiguous, String explanation) {
            this.resolvedMedicineName = resolvedMedicineName;
            this.category = category;
            this.match = match;
            this.ambiguous = ambiguous;
            this.explanation = explanation;
        }

        public String getResolvedMedicineName() {
            return resolvedMedicineName;
        }

        public String getCategory() {
            return category;
        }

        public boolean isMatch() {
            return match;
        }

        public boolean isAmbiguous() {
            return ambiguous;
        }

        public String getExplanation() {
            return explanation;
        }
    }

    public CatalogResolutionResult resolveCatalogMedicine(String rawTerm) {
        if (!StringUtils.hasText(rawTerm)) {
            return new CatalogResolutionResult(null, null, false, false, "Query term is empty.");
        }
        String cleanTerm = rawTerm.trim();

        // 1. Exact match check (case-insensitive name or brand)
        List<Medicine> exactMatches = medicineRepository.findExactByNameOrBrand(cleanTerm);
        if (!exactMatches.isEmpty()) {
            Set<String> distinctNames = exactMatches.stream()
                    .map(Medicine::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (distinctNames.size() == 1) {
                Medicine m = exactMatches.get(0);
                String expl = "Resolved query to catalog medicine: " + m.getName() +
                        (m.getBrand() != null ? " (Brand: " + m.getBrand() + ")" : "");
                return new CatalogResolutionResult(m.getName(), m.getCategory(), true, false, expl);
            } else {
                // Ambiguous exact matches across distinct formulations
                String expl = "Multiple distinct catalog medicines found for exact term '" + cleanTerm + "': " +
                        String.join(", ", distinctNames) + ". Please specify the exact dosage formulation.";
                return new CatalogResolutionResult(null, null, false, true, expl);
            }
        }

        // 2. Partial substring match check
        List<Medicine> partialMatches = medicineRepository.searchByNameOrBrand(cleanTerm);
        if (!partialMatches.isEmpty()) {
            Set<String> distinctNames = partialMatches.stream()
                    .map(Medicine::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (distinctNames.size() == 1) {
                Medicine m = partialMatches.get(0);
                String expl = "Resolved query to catalog medicine: " + m.getName() +
                        (m.getBrand() != null ? " (Brand: " + m.getBrand() + ")" : "");
                return new CatalogResolutionResult(m.getName(), m.getCategory(), true, false, expl);
            } else {
                // Ambiguous partial matches
                String expl = "Ambiguous query. Multiple matching medicines found in catalog: " +
                        String.join(", ", distinctNames) + ". Please specify the exact product or formulation.";
                return new CatalogResolutionResult(null, null, false, true, expl);
            }
        }

        // 3. No match found in catalog
        return new CatalogResolutionResult(null, null, false, false,
                "No matching medicine found in SentinelGrid's catalog for '" + cleanTerm + "'. Please check the spelling or search by brand name.");
    }

    public boolean containsSymptomKeyword(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            return false;
        }
        String lower = prompt.toLowerCase();
        for (String symptom : SYMPTOM_KEYWORDS) {
            if (Pattern.compile("\\b" + Pattern.quote(symptom) + "\\b", Pattern.CASE_INSENSITIVE).matcher(lower).find()) {
                return true;
            }
        }
        return false;
    }

    public List<Medicine> findCatalogMedicinesContainedInQuery(String query) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String lowerQuery = query.toLowerCase();
        List<Medicine> allMedicines = medicineRepository.findAll();
        if (allMedicines == null || allMedicines.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> matchedIds = new HashSet<>();
        List<Medicine> matched = new ArrayList<>();

        for (Medicine m : allMedicines) {
            boolean matches = false;
            // Check brand
            if (StringUtils.hasText(m.getBrand())) {
                String brandLower = m.getBrand().toLowerCase();
                if (Pattern.compile("\\b" + Pattern.quote(brandLower) + "\\b", Pattern.CASE_INSENSITIVE).matcher(lowerQuery).find()) {
                    matches = true;
                }
            }
            // Check full name
            if (!matches && StringUtils.hasText(m.getName())) {
                String nameLower = m.getName().toLowerCase();
                if (lowerQuery.contains(nameLower)) {
                    matches = true;
                }
            }
            // Check significant name tokens (e.g. "Remdesivir" from "Remdesivir 100mg Injection", "Anti-Venom" from "Anti-Venom Polyvalent Injection")
            if (!matches && StringUtils.hasText(m.getName())) {
                String primaryNamePart = extractPrimaryNamePart(m.getName());
                if (StringUtils.hasText(primaryNamePart) && primaryNamePart.length() >= 3) {
                    if (Pattern.compile("\\b" + Pattern.quote(primaryNamePart.toLowerCase()) + "\\b", Pattern.CASE_INSENSITIVE).matcher(lowerQuery).find()) {
                        matches = true;
                    } else if (primaryNamePart.contains("-")) {
                        // Also try without hyphens e.g. "antivenom" vs "anti-venom"
                        String noHyphenPrimary = primaryNamePart.replace("-", "").toLowerCase();
                        String noHyphenQuery = lowerQuery.replace("-", "");
                        if (Pattern.compile("\\b" + Pattern.quote(noHyphenPrimary) + "\\b", Pattern.CASE_INSENSITIVE).matcher(noHyphenQuery).find()) {
                            matches = true;
                        }
                    }
                }
            }

            if (matches && matchedIds.add(m.getId())) {
                matched.add(m);
            }
        }
        return matched;
    }

    private String extractPrimaryNamePart(String fullName) {
        if (!StringUtils.hasText(fullName)) return "";
        // Strip common dosage and formulation suffixes: e.g. " 100mg", " 400mg", " Injection", " 10L"
        String cleaned = fullName.replaceAll("(?i)\\s+(\\d+\\s*(mg|g|ml|l)\\b.*)", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\s+(Polyvalent|Injection|Tablet|Capsules?|Syrup|Solution)\\b.*", "").trim();
        return cleaned;
    }

    public SearchNormalizationResponse normalizeAndSearch(SearchNormalizationRequest request) {
        String prompt = request.getPrompt() != null ? request.getPrompt().trim() : "";
        String city = request.getCity() != null ? request.getCity().trim() : null;

        if (prompt.isEmpty()) {
            return SearchNormalizationResponse.builder()
                    .originalPrompt("")
                    .normalizedMedicine(null)
                    .category(null)
                    .explanation("Query is empty. Please enter a medicine name or brand.")
                    .catalogMatch(false)
                    .symptomQuery(false)
                    .searchResults(Collections.emptyList())
                    .build();
        }

        boolean hasSymptoms = containsSymptomKeyword(prompt);
        if (hasSymptoms) {
            // Check if the query also specifies an actual catalog medicine (compound query)
            List<Medicine> referencedMedicines = findCatalogMedicinesContainedInQuery(prompt);

            // If findCatalogMedicinesContainedInQuery was empty, check if direct prompt or fallback matches
            if (referencedMedicines.isEmpty()) {
                CatalogResolutionResult directCheck = resolveCatalogMedicine(prompt);
                if (directCheck.isMatch()) {
                    List<Medicine> mList = medicineRepository.searchByNameOrBrand(directCheck.getResolvedMedicineName());
                    if (!mList.isEmpty()) {
                        referencedMedicines = mList;
                    }
                }
            }

            // Also check fallback mapping (e.g. "Anti-Venom for snake bite", "covifor for fever")
            if (referencedMedicines.isEmpty()) {
                Map<String, String> localData = fallbackLocalCatalogMatch(prompt);
                String fallbackTerm = localData.get("normalizedMedicine");
                if (StringUtils.hasText(fallbackTerm) && !fallbackTerm.equalsIgnoreCase(prompt)) {
                    CatalogResolutionResult fallbackCheck = resolveCatalogMedicine(fallbackTerm);
                    if (fallbackCheck.isMatch()) {
                        List<Medicine> mList = medicineRepository.searchByNameOrBrand(fallbackCheck.getResolvedMedicineName());
                        if (!mList.isEmpty()) {
                            referencedMedicines = mList;
                        }
                    }
                }
            }

            Set<String> distinctReferencedNames = referencedMedicines.stream()
                    .map(Medicine::getName)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (distinctReferencedNames.size() == 1) {
                Medicine primaryMatch = referencedMedicines.get(0);
                String validatedMedicineName = primaryMatch.getName();
                String category = primaryMatch.getCategory();
                String explanation = "Resolved query to catalog medicine: " + primaryMatch.getName() +
                        (primaryMatch.getBrand() != null ? " (Brand: " + primaryMatch.getBrand() + ")" : "") +
                        ". Note: SentinelGrid locates pharmacy stock for requested items and does not verify medical appropriateness for specific symptoms. Please consult a qualified healthcare professional.";
                List<InventoryDto> searchResults = medicineService.searchMedicines(validatedMedicineName, city);
                return SearchNormalizationResponse.builder()
                        .originalPrompt(prompt)
                        .normalizedMedicine(validatedMedicineName)
                        .category(category)
                        .explanation(explanation)
                        .catalogMatch(true)
                        .symptomQuery(false)
                        .searchResults(searchResults)
                        .build();
            } else if (distinctReferencedNames.size() > 1) {
                return SearchNormalizationResponse.builder()
                        .originalPrompt(prompt)
                        .normalizedMedicine(null)
                        .category(null)
                        .explanation("Multiple catalog medicines detected in query: " + String.join(", ", distinctReferencedNames) + ". Please search for one specific medicine at a time.")
                        .catalogMatch(false)
                        .symptomQuery(false)
                        .searchResults(Collections.emptyList())
                        .build();
            } else {
                // Pure symptom or symptom + unknown entity: strictly refuse medical recommendations
                log.info("Symptom query without catalog medicine detected: '{}'. Refusing medical recommendation.", prompt);
                return SearchNormalizationResponse.builder()
                        .originalPrompt(prompt)
                        .normalizedMedicine(null)
                        .category(null)
                        .explanation("Symptom description detected. SentinelGrid is a pharmacy inventory locator and does not provide medical diagnosis or recommend medicines for symptoms. Please consult a qualified doctor or emergency services.")
                        .catalogMatch(false)
                        .symptomQuery(true)
                        .searchResults(Collections.emptyList())
                        .build();
            }
        }

        // Query did not contain symptoms; check upstream AI assistance if configured and not rate-limited
        String rawProposedTerm = null;
        String category = null;
        String explanation = null;

        boolean rateLimitExceeded = isRateLimitExceeded();

        if (StringUtils.hasText(apiKey) && !rateLimitExceeded) {
            try {
                log.info("Calling Gemini API for search terminology clarification");
                JsonNode geminiResponse = callGeminiApi(prompt);
                if (geminiResponse != null) {
                    boolean aiFlaggedSymptom = geminiResponse.path("isSymptomQuery").asBoolean(false);
                    if (aiFlaggedSymptom) {
                        return SearchNormalizationResponse.builder()
                                .originalPrompt(prompt)
                                .normalizedMedicine(null)
                                .category(null)
                                .explanation("Symptom query detected. Please consult a healthcare professional for diagnosis and prescriptions.")
                                .catalogMatch(false)
                                .symptomQuery(true)
                                .searchResults(Collections.emptyList())
                                .build();
                    }
                    rawProposedTerm = geminiResponse.path("normalizedMedicine").asText(null);
                    category = geminiResponse.path("category").asText(null);
                    explanation = geminiResponse.path("explanation").asText(null);
                }
            } catch (ResourceAccessException e) {
                // Do not log the exception message: RestTemplate can include the request URL,
                // and this provider authenticates with a query parameter.
                log.warn("Gemini API connection/read timeout (3s exceeded); using local catalog fallback.");
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                // Keep the provider status for operations while never logging a URL or key.
                log.warn("Gemini API returned HTTP status {}; using local catalog fallback.", e.getStatusCode());
            } catch (Exception e) {
                log.warn("Gemini API call failed; using local catalog fallback.");
            }
        } else if (rateLimitExceeded) {
            log.warn("AI search assist rate limit reached. Proceeding with local catalog search.");
        }

        // Local catalog resolution if AI was unavailable, unconfigured, rate-limited, or returned no term
        if (!StringUtils.hasText(rawProposedTerm)) {
            CatalogResolutionResult directResolution = resolveCatalogMedicine(prompt);
            if (directResolution.isMatch()) {
                String validatedMedicineName = directResolution.getResolvedMedicineName();
                String resolvedCategory = directResolution.getCategory();
                String expl = directResolution.getExplanation();
                List<InventoryDto> searchResults = medicineService.searchMedicines(validatedMedicineName, city);
                return SearchNormalizationResponse.builder()
                        .originalPrompt(prompt)
                        .normalizedMedicine(validatedMedicineName)
                        .category(resolvedCategory)
                        .explanation(expl)
                        .catalogMatch(true)
                        .symptomQuery(false)
                        .searchResults(searchResults)
                        .build();
            } else if (directResolution.isAmbiguous()) {
                return SearchNormalizationResponse.builder()
                        .originalPrompt(prompt)
                        .normalizedMedicine(null)
                        .category(null)
                        .explanation(directResolution.getExplanation())
                        .catalogMatch(false)
                        .symptomQuery(false)
                        .searchResults(Collections.emptyList())
                        .build();
            }

            Map<String, String> localData = fallbackLocalCatalogMatch(prompt);
            rawProposedTerm = localData.get("normalizedMedicine");
            category = localData.get("category");
            explanation = localData.get("explanation");
        }

        // Safety Guard: Validate any proposed term deterministically against catalog
        String validatedMedicineName = null;
        boolean catalogMatch = false;
        List<InventoryDto> searchResults = Collections.emptyList();

        if (StringUtils.hasText(rawProposedTerm)) {
            CatalogResolutionResult proposedResolution = resolveCatalogMedicine(rawProposedTerm);
            if (proposedResolution.isMatch()) {
                validatedMedicineName = proposedResolution.getResolvedMedicineName();
                category = proposedResolution.getCategory();
                catalogMatch = true;
                explanation = proposedResolution.getExplanation();
                searchResults = medicineService.searchMedicines(validatedMedicineName, city);
            } else if (proposedResolution.isAmbiguous()) {
                catalogMatch = false;
                validatedMedicineName = null;
                category = null;
                explanation = proposedResolution.getExplanation();
            } else {
                catalogMatch = false;
                validatedMedicineName = null;
                category = null;
                explanation = "No matching medicine found in SentinelGrid's catalog for '" + prompt + "'. Please check the spelling or search by brand name.";
            }
        } else {
            explanation = "Unable to identify a matching catalog medicine for the entered search terms.";
        }

        return SearchNormalizationResponse.builder()
                .originalPrompt(prompt)
                .normalizedMedicine(validatedMedicineName)
                .category(category)
                .explanation(explanation)
                .catalogMatch(catalogMatch)
                .symptomQuery(false)
                .searchResults(searchResults)
                .build();
    }

    public boolean isRateLimitExceeded() {
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            if (now - windowStartTime > 60000) {
                windowStartTime = now;
                requestCounter = 0;
            }
            requestCounter++;
            return requestCounter > MAX_REQUESTS_PER_MINUTE;
        }
    }

    public void resetRateLimiterForTesting() {
        synchronized (rateLimitLock) {
            requestCounter = 0;
            windowStartTime = System.currentTimeMillis();
        }
    }

    private JsonNode callGeminiApi(String userPrompt) throws Exception {
        String fullUrl = apiUrl + "?key=" + apiKey;

        String systemInstruction = "You are a terminology clarification assistant for an emergency medicine availability platform (SentinelGrid). " +
                "The platform holds verified emergency stocks including: " +
                "- Remdesivir 100mg Injection (Brand: Covifor) [ANTIVIRAL] " +
                "- Tocilizumab 400mg (Brand: Actemra) [IMMUNOSUPPRESSANT] " +
                "- Enoxaparin 40mg Injection (Brand: Clexane) [ANTICOAGULANT] " +
                "- Favipiravir 400mg (Brand: FabiFlu) [ANTIVIRAL] " +
                "- Oxygen Cylinder 10L (Brand: MedO2) [RESPIRATORY_SUPPORT] " +
                "- Anti-Venom Polyvalent Injection (Brand: VenoCure) [EMERGENCY_ANTIVENOM] for snake bites, snake envenomation, and snake attacks. " +
                "CRITICAL RULES: " +
                "1. If the user query is about snake bites, envenomation, or snake attacks, resolve to: " +
                "{\"normalizedMedicine\": \"Anti-Venom Polyvalent Injection\", \"category\": \"EMERGENCY_ANTIVENOM\", \"isSymptomQuery\": false, \"explanation\": \"Resolved query to emergency antidote: Anti-Venom Polyvalent Injection (Brand: VenoCure).\"} " +
                "2. If the user mentions emergency oxygen needs (e.g. oxygen cylinder, breathing gas), resolve to Oxygen Cylinder 10L. " +
                "3. If the user query describes vague illness/symptoms (e.g. 'I have a headache', 'what to take for fever') without naming a medicine or emergency antidote, output: " +
                "{\"normalizedMedicine\": null, \"category\": null, \"isSymptomQuery\": true, \"explanation\": \"Symptoms detected. Please consult a doctor for diagnosis and prescriptions.\"} " +
                "4. If the user enters a specific medicine, brand, or synonym, extract the exact catalog name. " +
                "5. Respond ONLY with a valid JSON object matching this schema. Never invent nonexistent medicines.";

        Map<String, Object> contents = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", systemInstruction + "\nUser Query: " + userPrompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(contents, headers);

        ResponseEntity<String> response = getRestTemplate().exchange(fullUrl, HttpMethod.POST, entity, String.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                JsonNode parts = candidate.path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).path("text").asText("");
                    text = text.replaceAll("```json", "").replaceAll("```", "").trim();
                    if (!text.isEmpty()) {
                        return objectMapper.readTree(text);
                    }
                }
            }
        }
        return null;
    }

    private Map<String, String> fallbackLocalCatalogMatch(String prompt) {
        Map<String, String> res = new HashMap<>();

        // Check local database catalog first
        List<Medicine> matches = medicineRepository.searchByNameOrBrand(prompt);
        if (!matches.isEmpty()) {
            Medicine m = matches.get(0);
            res.put("normalizedMedicine", m.getName());
            res.put("category", m.getCategory());
            res.put("explanation", "Matched catalog medicine: " + m.getName());
            return res;
        }

        // Deterministic known brand/catalog mapping
        String lower = prompt.toLowerCase();
        if (lower.contains("covifor")) {
            res.put("normalizedMedicine", "Remdesivir 100mg Injection");
            res.put("category", "ANTIVIRAL");
            res.put("explanation", "Matched brand Covifor to Remdesivir 100mg Injection.");
        } else if (lower.contains("actemra")) {
            res.put("normalizedMedicine", "Tocilizumab 400mg");
            res.put("category", "IMMUNOSUPPRESSANT");
            res.put("explanation", "Matched brand Actemra to Tocilizumab 400mg.");
        } else if (lower.contains("clexane")) {
            res.put("normalizedMedicine", "Enoxaparin 40mg Injection");
            res.put("category", "ANTICOAGULANT");
            res.put("explanation", "Matched brand Clexane to Enoxaparin 40mg Injection.");
        } else if (lower.contains("fabiflu")) {
            res.put("normalizedMedicine", "Favipiravir 400mg");
            res.put("category", "ANTIVIRAL");
            res.put("explanation", "Matched brand FabiFlu to Favipiravir 400mg.");
        } else if (lower.contains("medo2") || lower.contains("oxygen cylinder")) {
            res.put("normalizedMedicine", "Oxygen Cylinder 10L");
            res.put("category", "RESPIRATORY_SUPPORT");
            res.put("explanation", "Matched query to Oxygen Cylinder 10L.");
        } else if (lower.contains("venocure") || lower.contains("anti-venom") || lower.contains("antivenom")
                || lower.contains("anti snake") || lower.contains("anti-snake") || lower.contains("snake venom")
                || lower.contains("snake venom antiserum") || lower.contains("snake bite") || lower.contains("snake")
                || lower.contains("bitten by snake") || lower.contains("polyvalent")) {
            res.put("normalizedMedicine", "Anti-Venom Polyvalent Injection");
            res.put("category", "EMERGENCY_ANTIVENOM");
            res.put("explanation", "Matched query to Anti-Venom Polyvalent Injection (Brand: VenoCure).");
        } else {
            // Default: query terms used directly without hallucination
            res.put("normalizedMedicine", prompt);
            res.put("category", null);
            res.put("explanation", "Utilized query terms directly for catalog verification.");
        }
        return res;
    }
}
