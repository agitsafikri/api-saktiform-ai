package com.saktiform.api.service.chat.bot;

import com.saktiform.api.entity.Chat;

import java.util.UUID;

public interface BotDecisionService {
    Boolean shouldBotReply(Chat chat);
}
