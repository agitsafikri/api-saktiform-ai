package com.saktiform.api.model.chat.bot;

import java.util.UUID;

public class IncomingChatEvent {

    private final UUID chatId;

    public IncomingChatEvent(UUID chatId) {
        this.chatId = chatId;
    }

    public UUID getChatId() {
        return chatId;
    }

}
