package com.saktiform.api.service.chat.bot;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.*;
import com.saktiform.api.model.chat.bot.ChatContext;
import com.saktiform.api.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiLlmService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmService.class);



    private final AppConfigService appConfigService;

    private final AiClientFactory clientFactory;

    public OpenAiLlmService(AiClientFactory clientFactory, AppConfigService appConfigService) {
       this.clientFactory = clientFactory;
       this.appConfigService = appConfigService;

    }


    public String generateReply(
            String systemPrompt,
            ChatContext context
    ) {
        if (context.getOrderInfo() == null || context.getOrderInfo().isBlank()) {
            return "NULL";
        }
        var temperature = Double.valueOf(appConfigService.getConfig("ai.temperature"));
        var model = appConfigService.getConfig("openai.model");
        var maxTokens = Integer.valueOf(appConfigService.getConfig("ai.max.tokens"));

        OpenAIClient client = clientFactory.buildClient();

            ChatCompletionCreateParams.Builder builder =
                    ChatCompletionCreateParams.builder()
                            .model(ChatModel.of(model))
                            .maxTokens(maxTokens)
                            .temperature(temperature);

            //  SYSTEM INSTRUCTION (WAJIB)
            builder.addMessage(
                    ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                            ChatCompletionSystemMessageParam.builder()
                                    .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                                    .content(systemPrompt)
                                    .build()
                    )
            );

            //  SYSTEM INSTRUCTION (WAJIB)
            builder.addMessage(
                    ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                            ChatCompletionSystemMessageParam.builder()
                                    .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                                    .content(context.getOrderInfo())
                                    .build()
                    )
            );

            // History chat
            context.getMessages().forEach(message -> {
               if (message.getPengirim().equalsIgnoreCase("CUSTOMER")) {
                   builder.addMessage(
                           ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                                   ChatCompletionUserMessageParam.builder()
                                           .role(ChatCompletionUserMessageParam.Role.USER)
                                           .content(message.getPesan())
                                           .build()
                           )
                   );
               } else {
                   builder.addMessage(
                           ChatCompletionMessageParam.ofChatCompletionAssistantMessageParam(
                                   ChatCompletionAssistantMessageParam.builder()
                                           .role(ChatCompletionAssistantMessageParam.Role.ASSISTANT)
                                           .content(message.getPesan())
                                           .build()
                           )
                   );
               }
            });


            // USER MESSAGE (yang dijawab AI)
            builder.addMessage(
                    ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                            ChatCompletionUserMessageParam.builder()
                                    .role(ChatCompletionUserMessageParam.Role.USER)
                                    .content(context.getUserMessage())
                                    .build()
                    )
            );

            ChatCompletion completion =
                    client.chat().completions().create(builder.build());

            if (completion.choices().isEmpty()) {
                return "NULL";
            }

            String result = completion.choices()
                    .get(0)
                    .message()
                    .content()
                    .orElse("")
                    .trim();

            if (result.isBlank()) {
                return "NULL";
            }

            return result;


    }

    public String guardrailCheck(String prompt, String userMessage) {
        OpenAIClient client = clientFactory.buildClient();

        ChatCompletion completion =
                client.chat().completions().create(
                        ChatCompletionCreateParams.builder()
                                .model(ChatModel.of(appConfigService.getConfig("openai.model")))
                                .temperature(0) // penting: deterministik
                                .maxTokens(5)
                                .addMessage(ChatCompletionMessageParam.ofChatCompletionSystemMessageParam(
                                        ChatCompletionSystemMessageParam.builder()
                                                .role(ChatCompletionSystemMessageParam.Role.SYSTEM)
                                                .content(prompt)
                                                .build()
                                ))
                                .addMessage(ChatCompletionMessageParam.ofChatCompletionUserMessageParam(
                                        ChatCompletionUserMessageParam.builder()
                                                .role(ChatCompletionUserMessageParam.Role.USER)
                                                .content(userMessage)
                                                .build()
                                ))
                                .build()
                );

        return completion.choices().get(0).message().content().orElse("").trim();
    }

}
