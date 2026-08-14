package com.sentragrid.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentragrid.ai.dto.SearchNormalizationRequest;
import com.sentragrid.ai.dto.SearchNormalizationResponse;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.service.MedicineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Medicine Search Assistant Service.
 * Uses Google Gemini API (or intelligent fallback rule engine) to normalize
 * natural language symptoms/queries into structured medicine search terms.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiSearchService {

    private final MedicineService medicineService;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent}")
    private String apiUrl;

    public SearchNormalizationResponse normalizeAndSearch(SearchNormalizationRequest request) {
        String prompt = request.getPrompt();
        String city = request.getCity();

        String normalizedMedicine = null;
        String category = null;
        String explanation = null;

        if (StringUtils.hasText(apiKey)) {
            try {
                log.info("Calling Gemini API to normalize prompt: '{}'", prompt);
                JsonNode geminiResponse = callGeminiApi(prompt);
                if (geminiResponse != null) {
                    normalizedMedicine = geminiResponse.path("normalizedMedicine").asText(null);
                    category = geminiResponse.path("category").asText(null);
                    explanation = geminiResponse.path("explanation").asText(null);
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed, falling back to rule engine: {}", e.getMessage());
            }
        }

        // Fallback intelligent normalization engine for offline demo / missing API key
        if (!StringUtils.hasText(normalizedMedicine)) {
            Map<String, String> normalizedData = fallbackNormalize(prompt);
            normalizedMedicine = normalizedData.get("normalizedMedicine");
            category = normalizedData.get("category");
            explanation = normalizedData.get("explanation");
        }

        // Execute inventory search using normalized term
        List<InventoryDto> searchResults = medicineService.searchMedicines(normalizedMedicine, city);

        return SearchNormalizationResponse.builder()
                .originalPrompt(prompt)
                .normalizedMedicine(normalizedMedicine)
                .category(category)
                .explanation(explanation)
                .searchResults(searchResults)
                .build();
    }

    private JsonNode callGeminiApi(String userPrompt) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        String fullUrl = apiUrl + "?key=" + apiKey;

        String systemInstruction = "You are a medical search assistant for an emergency medicine platform. " +
                "Given a natural language query (e.g. 'snake bite injection', 'blood thinner for clot', 'severe covid antiviral'), " +
                "extract or infer the exact medicine name, medical category, and brief 1-sentence explanation. " +
                "Respond ONLY with a valid JSON object matching this schema: {\"normalizedMedicine\": \"...\", \"category\": \"...\", \"explanation\": \"...\"}";

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

        ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.POST, entity, String.class);
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
            // Clean markdown code fence formatting if returned by Gemini
            text = text.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readTree(text);
        }
        return null;
    }

    private Map<String, String> fallbackNormalize(String prompt) {
        String lower = prompt.toLowerCase();
        Map<String, String> res = new HashMap<>();

        if (lower.contains("snake") || lower.contains("bite") || lower.contains("venom")) {
            res.put("normalizedMedicine", "Anti-Venom Polyvalent");
            res.put("category", "EMERGENCY_ANTIVENOM");
            res.put("explanation", "Mapped snake bite query to emergency Anti-Venom Polyvalent injection.");
        } else if (lower.contains("antiviral") || lower.contains("covid") || lower.contains("viral")) {
            res.put("normalizedMedicine", "Remdesivir");
            res.put("category", "ANTIVIRAL");
            res.put("explanation", "Inferred critical antiviral treatment Remdesivir from query.");
        } else if (lower.contains("blood") || lower.contains("thinner") || lower.contains("clot") || lower.contains("heparin")) {
            res.put("normalizedMedicine", "Enoxaparin");
            res.put("category", "ANTICOAGULANT");
            res.put("explanation", "Mapped blood thinner query to Enoxaparin anticoagulant injection.");
        } else if (lower.contains("cytokine") || lower.contains("immuno") || lower.contains("actemra")) {
            res.put("normalizedMedicine", "Tocilizumab");
            res.put("category", "IMMUNOSUPPRESSANT");
            res.put("explanation", "Mapped query to Tocilizumab immunosuppressant injection.");
        } else if (lower.contains("oxygen") || lower.contains("breathing") || lower.contains("respiratory")) {
            res.put("normalizedMedicine", "Oxygen Cylinder");
            res.put("category", "RESPIRATORY_SUPPORT");
            res.put("explanation", "Mapped query to Medical Oxygen Cylinder 10L.");
        } else {
            // Default generic normalization
            res.put("normalizedMedicine", prompt);
            res.put("category", "GENERAL");
            res.put("explanation", "Utilized query terms directly for medicine inventory search.");
        }
        return res;
    }
}
