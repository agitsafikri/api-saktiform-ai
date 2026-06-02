package com.saktiform.api.service.chat.bot;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.bot.ChatContext;
import com.saktiform.api.service.chat.ChatMessageService;
import com.saktiform.api.service.chat.MessageConstructorHelper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContextBuilderService {
    private final ChatMessageService chatMessageService;
    private final MessageConstructorHelper messageConstructorHelper;


    public ContextBuilderService(ChatMessageService chatMessageService, MessageConstructorHelper messageConstructorHelper) {
        this.chatMessageService = chatMessageService;
        this.messageConstructorHelper = messageConstructorHelper;
    }

    public ChatContext build(UUID conversationId) {

        List<Chat> messages =chatMessageService.getRecentCustomerTextMessages(conversationId, 10);
        String userMessage = messages.get(0).getPesan();
        messages.remove(0);


        return new ChatContext(
                conversationId,
                userMessage,
                messages.reversed(),
                messageConstructorHelper.getOrderSystemInfo(conversationId)
        );

    }

}
