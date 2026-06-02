package com.saktiform.api.repository;

import com.saktiform.api.entity.Chat;
import com.saktiform.api.model.chat.ChatListDto;
import com.saktiform.api.service.chat.WhatsappClientHelper;
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
                    c.sentAt,
                    rc.id,
                    rc.type,
                    rc.pengirim,
                    rc.pesan,
                    rc.media,
                    rc.sentAt
                )  FROM Chat c 
                        Left JOIN c.repliedTo rc
                WHERE c.idConversation = :idConversation
                    AND LOWER( COALESCE(c.pesan, '')) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
        """)
    Page<ChatListDto> getMessageList(@Param("idConversation") UUID idConversation, @Param("keyword") String keyword, Pageable pageable);

    List<Chat> findByIdConversationOrderBySentAtDesc(UUID idConversation);

    boolean existsByIdConversationAndSentAtGreaterThan(UUID idConversation, Instant sentAt);

    @Query("""
        SELECT c
        FROM Chat c
        WHERE c.idConversation = :conversationId
        ORDER BY c.sentAt DESC
    """)
    List<Chat> findRecentCustomerTextMessages(
            @Param("conversationId") UUID conversationId,
            Pageable pageable
    );

    Chat findByMessageId(String messageId);
}