package com.saktiform.api.service.chat;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.repository.ChatRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ChatMessageService {
    private final ChatRepository chatRepository;
    public ChatMessageService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public Chat saveChat(Chat chat){
        return chatRepository.save(chat);
    }

    public boolean hasNewMessageAfter(UUID conversationId, Instant sentAt) {
        var result = chatRepository.existsByIdConversationAndSentAtGreaterThan(conversationId, sentAt);
        return result;
    }

    public Chat findByIdConversationOrderBySentAtDesc(UUID idConversation){
        return chatRepository.findByIdConversationOrderBySentAtDesc(idConversation).getFirst();
    }

    public Chat findById(UUID id){
        return chatRepository.findById(id).get();
    }

    public List<Chat> getRecentCustomerTextMessages(UUID idConversation, int limit){
        List<Chat> result = chatRepository.findRecentCustomerTextMessages(idConversation, PageRequest.of(0, limit));
        Collections.reverse(result);
        return result;
    }


}
