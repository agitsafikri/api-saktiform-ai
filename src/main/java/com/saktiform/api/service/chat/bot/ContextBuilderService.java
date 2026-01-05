package com.saktiform.api.service.chat.bot;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.bot.ChatContext;
import com.saktiform.api.service.chat.ChatMessageService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContextBuilderService {
    private final ChatMessageService chatMessageService;

    public ContextBuilderService(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    public ChatContext build(UUID conversationId) {

        List<Chat> messages =
                chatMessageService.getRecentCustomerTextMessages(
                        conversationId, 5
                );

        String combinedText = messages.stream()
                .sorted(Comparator.comparing(Chat::getSentAt))
                .map(Chat::getPesan)
                .collect(Collectors.joining("\n"));

        return new ChatContext(
                conversationId,
                combinedText,
                messages
        );
    }

}
