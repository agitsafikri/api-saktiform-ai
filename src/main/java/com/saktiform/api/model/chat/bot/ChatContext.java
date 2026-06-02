package com.saktiform.api.model.chat.bot;

import com.saktiform.api.entity.Chat;

import java.util.List;
import java.util.UUID;

public class ChatContext {

    private final UUID conversationId;
    private final String userMessage;
    private final List<Chat> messages;
    private final String orderInfo;

    public ChatContext(UUID conversationId,
                       String userMessage,
                       List<Chat> messages,
                       String orderInfo) {
        this.conversationId = conversationId;
        this.userMessage = userMessage;
        this.messages = messages;
        this.orderInfo = orderInfo;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public List<Chat> getMessages() {
        return messages;
    }

    public String getOrderInfo() {
        return orderInfo;
    }
}

