package com.saktiform.api.service.chat.bot;

import com.saktiform.api.model.chat.ChatType;
import com.saktiform.api.model.chat.SendMessageDto;
import com.saktiform.api.model.chat.bot.ChatContext;
import com.saktiform.api.service.chat.ChatService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BotService {

    private final ChatService chatService;
    private final ContextBuilderService contextBuilderService;
    private final OpenAiLlmService openAiLlmService;
    private final GeminiLlmService geminiLlmService;
    private final QdrantVectorService qdrantVectorService;

    public BotService(ChatService chatService,
            ContextBuilderService contextBuilderService,
            OpenAiLlmService openAiLlmService,
            GeminiLlmService geminiLlmService,
            QdrantVectorService qdrantVectorService) {
        this.chatService = chatService;
        this.contextBuilderService = contextBuilderService;
        this.openAiLlmService = openAiLlmService;
        this.geminiLlmService = geminiLlmService;
        this.qdrantVectorService = qdrantVectorService;
    }

    public void handleBotReply(UUID conversationId) {

        ChatContext context = contextBuilderService.build(conversationId);

        // 1. Coba Rule Based
        String reply = ruleBasedReply(context);

        // 2. Jika tidak ada rule yang cocok, gunakan AI (RAG)
        if (reply == null) {
            reply = ragBasedReply(context);
        }

        // Fallback jika AI gagal / kosong
        if (reply == null || reply.isBlank()) {
            reply = "Terima kasih kak, kami bantu cek dulu ya 🙏";
        }

        var sendMessage = new SendMessageDto();
        sendMessage.setMessageType(ChatType.TEXT);
        sendMessage.setMessage(reply);
        sendMessage.setConversationId(conversationId);

        chatService.messageHandler(sendMessage, "BOT");

    }

    private String ruleBasedReply(ChatContext context) {

        String text = context.getUserMessage().toLowerCase();

        if (text.contains("harga")) {
            return "Untuk info harga, boleh sebutkan nama produknya ya kak 🙂";
        }

        if (text.contains("halo") || text.contains("hai")) {
            return "Halo kak 👋 Ada yang bisa kami bantu?";
        }

        return null;
    }

    private String ragBasedReply(ChatContext context) {
        try {
            String userMessage = context.getUserMessage();

            // RAG placeholder (mocking embedding/retrieval for now)
            // ... QdrantVectorService retrieval logic here ...

            String systemPrompt = """
                    Kamu adalah asisten AI untuk customer service.
                    Jawablah pertanyaan pelanggan dengan sopan, ramah, dan membantu.
                    Jika kamu tidak tahu jawabannya, arahkan mereka untuk menunggu admin.
                    """;

            // Switch to Gemini
            return geminiLlmService.generateReply(systemPrompt, userMessage);
            // return openAiLlmService.generateReply(systemPrompt, userMessage);

        } catch (Exception e) {
            return null;
        }
    }

}
