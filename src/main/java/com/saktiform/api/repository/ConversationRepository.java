package com.saktiform.api.repository;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.chat.ConversationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Conversation findByIdContact(Long idContact);

    @Query(value = """

            SELECT\s
            c.id as id,
            ct.nama_kontak as contactName,
            c.last_message_type as lastMessageType,
            c.last_message as lastMessage,
            c.last_message_at as lastMessageTimeRaw,
            c.status  as statusConversation,
            c.chat_status as chatStatus,
            c.status as status,
            c.unread_message_count as unreadMessageCount
        FROM public.conversation c
        JOIN contact ct ON ct.id = c.id_contact
        WHERE ct.id_workspace = :idWorkspace\s
            AND c.status = :statusConv
        
            
            AND (CAST(:dateStart AS timestamp) IS NULL OR c.last_message_at >= :dateStart)
            AND (CAST(:dateEnd AS timestamp) IS NULL OR c.last_message_at <= :dateEnd)
        
            AND (:unread IS NULL OR :unread = false OR c.unread_message_count > 0)
            AND (:agentId IS NULL OR c.handled_by = :agentId)
            AND (:chatStatus IS NULL OR c.chat_status ILIKE :chatStatus)
        
            
            AND (
                :orderStatus IS NULL\s
                OR EXISTS (
                    SELECT 1\s
                    FROM public.order ord\s
                    WHERE ord.id_conversation = c.id\s
                      AND ord.status = :orderStatus
                )
            )
        
            
            AND (
                :keyword IS NULL
                OR ct.nama_kontak ILIKE CONCAT('%', :keyword, '%')
                OR EXISTS (
                    SELECT 1
                    FROM public.order ord
                    WHERE ord.id_conversation = c.id
                      AND ord.order_code ILIKE CONCAT('%', :keyword, '%')
                )
                OR EXISTS (
                    SELECT 1
                    FROM chat msg
                    WHERE msg.id_conversation = c.id
                      AND msg.pesan ILIKE CONCAT('%', :keyword, '%')
                )
            )
        
        ORDER BY c.last_message_at DESC
        """,
            countQuery = """

                    SELECT COUNT(c.id)
                    FROM public.conversation c
                    JOIN contact ct ON ct.id = c.id_contact
                    WHERE ct.id_workspace = :idWorkspace
              AND c.status = :statusConv
              AND (CAST(:dateStart AS timestamp) IS NULL OR c.last_message_at >= :dateStart)
              AND (CAST(:dateEnd AS timestamp) IS NULL OR c.last_message_at <= :dateEnd)
            
              AND (:unread IS NULL OR :unread = false OR c.unread_message_count > 0)
              AND (:agentId IS NULL OR c.handled_by = :agentId)
              AND (:chatStatus IS NULL OR c.chat_status ILIKE :chatStatus)
            
              
              AND (
                    :orderStatus IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM public."order" ord
                        WHERE ord.id_conversation = c.id
                          AND ord.status = :orderStatus
                    )
                  )
            
              
              AND (
                    :keyword IS NULL
                    OR ct.nama_kontak ILIKE CONCAT('%', :keyword, '%')
                    OR EXISTS (
                        SELECT 1
                        FROM public."order" ord
                        WHERE ord.id_conversation = c.id
                          AND ord.order_code ILIKE CONCAT('%', :keyword, '%')
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM chat msg
                        WHERE msg.id_conversation = c.id
                          AND msg.pesan ILIKE CONCAT('%', :keyword, '%')
                    )
                  );
            
                """,
            nativeQuery = true)
    Page<ConversationDto> getConversation(@Param("idWorkspace") Long idWorkspace,
                                          @Param("statusConv") String statusConv,
                                          @Param("dateStart") LocalDateTime dateStart,
                                          @Param("dateEnd")LocalDateTime  dateEnd,
                                          @Param("sentinel")LocalDateTime sentinel,
                                          @Param("tomorow")LocalDateTime tomorow,
                                          @Param("unread")Boolean unread,
                                          @Param("agentId" )Long agentId,
                                          @Param("orderStatus")String orderStatus,
                                          @Param("keyword") String keyword,
                                          @Param("chatStatus") String chatStatus,
                                          Pageable pageable);

    @Query("""
        SELECT acc.username from Workspace w right join w.accounts acc where w.id = :idWorkspace OR acc.role = "OWNER"
    """)
    List<String> getAgentByWorkspace(@Param("idWorkspace") Long idWorkspace);

    @Query("""
    Select a.id from Account a where a.username = :username
        """)
    Long getAgentId(@Param("username") String username);
}