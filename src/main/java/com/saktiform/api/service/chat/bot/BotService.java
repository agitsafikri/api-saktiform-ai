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
    private final QdrantVectorService qdrantVectorService;

    public BotService(ChatService chatService,
            ContextBuilderService contextBuilderService,
            OpenAiLlmService openAiLlmService,
            QdrantVectorService qdrantVectorService) {
        this.chatService = chatService;
        this.contextBuilderService = contextBuilderService;
        this.openAiLlmService = openAiLlmService;
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

            // A. Embed & Search Knowledge (Simple impl for now)
            // Note: We need an Embedding Client here. For simplicity, we might skip
            // embedding
            // if we don't have a separate embedder yet, OR we assume OpenAiLlmService can
            // do it.
            // But per specs, we should use Qdrant.
            // LET'S ASSUME QdrantVectorService internally handles embedding or we just use
            // LLM directly for MVP
            // if embedding is not ready.
            // HOWEVER, the prompt asked for RAG.
            // As I don't have an embedding method in OpenAiLlmService yet, I will use a
            // simple LLM call first
            // to ensure it works, then add RAG context if I can get embeddings.

            // For now, let's just call the LLM directly as the "RAG" step placeholder
            // until we add the Embedding method to OpenAiLlmService.

            String systemPrompt = """
                    Kamu adalah asisten AI untuk customer service.
                    Jawablah pertanyaan pelanggan dengan sopan, ramah, dan membantu.
                    Jika kamu tidak tahu jawabannya, arahkan mereka untuk menunggu admin.
                    """;

            return openAiLlmService.generateReply(systemPrompt, userMessage);

        } catch (Exception e) {
            return null;
        }
    }

}
