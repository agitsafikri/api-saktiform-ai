package com.saktiform.api.service.chat.bot;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionCreateParams;
import com.openai.models.ChatCompletionMessageParam;
import com.openai.models.ChatCompletionSystemMessageParam;
import com.openai.models.ChatCompletionUserMessageParam;
import com.openai.models.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiLlmService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmService.class);

    private final OpenAIClient client;
    private final String model;

    public OpenAiLlmService(
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.model:gpt-3.5-turbo}") String model,
            @Value("${openai.timeout.seconds:60}") int timeoutSeconds) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "OpenAI API key is missing. Please set openai.api.key in application.properties");
        }

        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                // .timeout(Duration.ofSeconds(timeoutSeconds)) // If supported by builder, else
                // ignore for MVP
                .build();
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

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(ChatModel.of(model))
                    .addMessage(ChatCompletionMessageParam
                            .ofSystem(ChatCompletionSystemMessageParam.builder().content(finalSystemPrompt).build()))
                    .addMessage(ChatCompletionMessageParam
                            .ofUser(ChatCompletionUserMessageParam.builder().content(userMessage).build()))
                    .maxTokens(500)
                    .temperature(0.7)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);

            if (completion.choices().isEmpty()) {
                return "";
            }

            // The logic might differ slightly depending on SDK version,
            // usually it is .message().content().orElse("")
            return completion.choices().get(0).message().content().orElse("");

        } catch (Exception e) {
            log.error("Error calling OpenAI: {}", e.getMessage());
            return "Maaf, saat ini saya sedang mengalami gangguan. Mohon coba lagi nanti.";
        }
    }
}
