package com.saktiform.api.service.chat.bot;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.saktiform.api.service.AppConfigService;
import org.springframework.stereotype.Service;

@Service
public class AiClientFactory {

    private final AppConfigService appConfigService;

    public AiClientFactory(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    public OpenAIClient buildClient() {
        String apiKey = appConfigService.getConfig("AI_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_KEY belum diset di AppConfig");
        }

        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
    }
}
