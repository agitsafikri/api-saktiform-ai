package com.saktiform.api.repository;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.ChatListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @Query("""
        SELECT new com.saktiform.api.model.chat.ChatListDto(
                    c.id,
                    c.type,
                    c.pengirim,
                    c.pesan,
                    c.media,
                    c.sentAt
                )  FROM Chat c WHERE c.idConversation = :idConversation
        """)
    Page<ChatListDto> getMessageList(@Param("idConversation") UUID idConversation, Pageable pageable);

    List<Chat> findByIdConversationOrderBySentAtDesc(UUID idConversation);

    boolean existsByIdConversationAndSentAtGreaterThan(UUID idConversation, Instant sentAt);

    @Query("""
        SELECT c
        FROM Chat c
        WHERE c.idConversation = :conversationId
          AND c.pengirim = 'CUSTOMER'
          AND c.type = 'TEXT'
        ORDER BY c.sentAt DESC
    """)
    List<Chat> findRecentCustomerTextMessages(
            @Param("conversationId") UUID conversationId,
            Pageable pageable
    );
}