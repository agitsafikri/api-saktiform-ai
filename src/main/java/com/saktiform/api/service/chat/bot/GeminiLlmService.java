package com.saktiform.api.service.chat.bot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
public class GeminiLlmService {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmService.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiLlmService(
            @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.model:gemini-1.5-flash}") String model,
            @Value("${gemini.url:https://generativelanguage.googleapis.com/v1beta/models}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    public String generateReply(String systemPrompt, String userMessage) {
        return generateReply(systemPrompt, userMessage, null);
    }

    public String generateReply(String systemPrompt, String userMessage, String context) {
        try {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Gemini API Key is missing.");
                return "Maaf, konfigurasi AI belum lengkap.";
            }

            String fullPrompt = systemPrompt;
            if (context != null && !context.isBlank()) {
                fullPrompt += "\n\nCONTEXT:\n" + context;
            }
            // Add user message to the prompt logic implicitly or explicitly.
            // Usually System prompt + User Message.
            // Gemini doesn't strictly have "System" role in the same way as OpenAI in the
            // simplest API payload,
            // but we can prepend it or use the system_instruction (beta).
            // For simplicity/compatibility, we'll combine them or use user role.

            // Let's combine for now to be safe with standard generateContent
            String combinedContent = fullPrompt + "\n\nUser Question: " + userMessage;

            String url = String.format("%s/%s:generateContent?key=%s", baseUrl, model, apiKey);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            GeminiRequest request = new GeminiRequest(
                    List.of(new Content("user", List.of(new Part(combinedContent)))));

            HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

            GeminiResponse response = restTemplate.postForObject(url, entity, GeminiResponse.class);

            if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
                return response.getCandidates().get(0).getContent().getParts().get(0).getText();
            }

            return "Maaf, saya tidak mengerti.";

        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage());
            return "Maaf, saat ini saya sedang mengalami gangguan (Gemini).";
        }
    }

    // DTOs for Gemini API
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeminiRequest {
        private List<Content> contents;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Part {
        private String text;
    }

    @Data
    @NoArgsConstructor
    public static class GeminiResponse {
        private List<Candidate> candidates;
    }

    @Data
    @NoArgsConstructor
    public static class Candidate {
        private Content content;
    }
}
