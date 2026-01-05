package com.saktiform.api.model.chat.bot;

import com.saktiform.api.entity.Chat;

import java.util.List;
import java.util.UUID;

public class ChatContext {

    private final UUID conversationId;
    private final String userMessage;
    private final List<Chat> messages;

    public ChatContext(UUID conversationId,
                       String userMessage,
                       List<Chat> messages) {
        this.conversationId = conversationId;
        this.userMessage = userMessage;
        this.messages = messages;
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
}

