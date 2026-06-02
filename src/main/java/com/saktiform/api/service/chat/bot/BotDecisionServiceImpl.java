package com.saktiform.api.service.chat.bot;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.service.chat.ChatMessageService;
import com.saktiform.api.service.chat.ConversationService;
import com.saktiform.api.service.chat.ChatService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class BotDecisionServiceImpl implements BotDecisionService {
    private final ConversationService conversationService;
    private final ChatMessageService chatMessageService;
    public BotDecisionServiceImpl(ConversationService conversationService, ChatMessageService chatMessageService) {
        this.conversationService = conversationService;
        this.chatMessageService = chatMessageService;
    }

    @Override
    public Boolean shouldBotReply(Chat chat) {
        var conversation = conversationService.findById(chat.getIdConversation());
        if (!conversation.getHandleByBot()) {
            return false;
        }

        if(conversation.getBotQuota() == 0){
            return false;
        }

        // 1️⃣ text only
        if (!"TEXT".equalsIgnoreCase(chat.getType())) {
            return false;
        }

        // 2️⃣ pesan kosong
        if (chat.getPesan() == null || chat.getPesan().isBlank()) {
            return false;
        }



        // 3️⃣ conversation state


        return true;
    }
}
