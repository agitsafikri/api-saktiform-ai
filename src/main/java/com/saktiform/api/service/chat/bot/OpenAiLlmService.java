package com.saktiform.api.service.chat.bot;

import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class OpenAiLlmService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmService.class);

    private final OpenAiService openAiService;
    private final String model;

    public OpenAiLlmService(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.model:gpt-3.5-turbo}") String model,
            @Value("${openai.timeout.seconds:60}") int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "OpenAI API key is missing. Please set openai.api.key in application.properties");
        }
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(timeoutSeconds));
        this.model = model;
    }

    public String generateReply(String systemPrompt, String userMessage) {
        return generateReply(systemPrompt, userMessage, null);
    }

    public String generateReply(String systemPrompt, String userMessage, String context) {
        try {
            log.info("Generating LLM reply with model: {}", model);

            String finalSystemPrompt = systemPrompt;
            if (context != null && !context.isBlank()) {
                finalSystemPrompt += "\n\nCONTEXT:\n" + context;
            }

            ChatMessage systemMsg = new ChatMessage(ChatMessageRole.SYSTEM.value(), finalSystemPrompt);
            ChatMessage userMsg = new ChatMessage(ChatMessageRole.USER.value(), userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(model)
                    .messages(List.of(systemMsg, userMsg))
                    .maxTokens(500) // Adjustable
                    .temperature(0.7)
                    .build();

            return openAiService.createChatCompletion(request)
                    .getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            log.error("Error calling OpenAI: {}", e.getMessage());
            return "Maaf, saat ini saya sedang mengalami gangguan. Mohon coba lagi nanti.";
        }
    }
}
