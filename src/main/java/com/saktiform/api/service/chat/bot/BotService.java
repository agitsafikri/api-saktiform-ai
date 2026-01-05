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

    public BotService(ChatService chatService, ContextBuilderService contextBuilderService) {
        this.chatService = chatService;
        this.contextBuilderService = contextBuilderService;
    }

    public void handleBotReply(UUID conversationId) {

        ChatContext context = contextBuilderService.build(conversationId);

        String reply = ruleBasedReply(context);

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

        return "Terima kasih kak, kami bantu cek dulu ya 🙏";
    }

}
