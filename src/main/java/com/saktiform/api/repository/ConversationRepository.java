package com.saktiform.api.repository;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.chat.ConversationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Conversation findByIdContact(Long idContact);

//    @Query(value = """
//        SELECT c.id as id,
//                            ct.nama_kontak as contactName,
//                            ch_last.type as lastMessageType,
//                            ch_last.pesan as lastMessage,
//                            TO_CHAR(ch_last.sent_at, 'YYYY-MM-DD HH24:MI:SS') as lastMessageTime,
//                            c.status
//        FROM public.conversation c
//        JOIN contact ct ON ct.id = c.id_contact
//        join public.order o on c.id = o.id_conversation
//        join produk p on o.id_produk = p.id
//         left join Chat ch_last on ch_last.id = (
//                            SELECT ch.id FROM chat ch where ch.id_conversation = c.id order by ch.sent_at DESC LIMIT 1\s
//                        )
//        where p.id_workspace = :idWorkspace AND c.status = :statusConv
//        """,
//        countQuery = """
//
//                SELECT COUNT(DISTINCT c.id)
//            FROM public.conversation c
//            JOIN contact ct ON ct.id = c.id_contact
//            JOIN public.order o ON c.id = o.id_conversation
//            JOIN produk p ON o.id_produk = p.id
//            WHERE p.id_workspace = :idWorkspace
//              AND c.status = :statusConv
//                """,
//    nativeQuery = true)
//    Page<ConversationDto> getConversation(@Param("idWorkspace") Long idWorkspace, @Param("statusConv") String statusConv, Pageable pageable);

    @Query(value = """
        SELECT c.id as id,
                            ct.nama_kontak as contactName,
                            ch_last.type as lastMessageType,
                            ch_last.pesan as lastMessage,
                            TO_CHAR(ch_last.sent_at, 'YYYY-MM-DD HH24:MI') as lastMessageTime,
                            c.status,
                            c.unread_message_count as unreadMessageCount
        FROM public.conversation c
        JOIN contact ct ON ct.id = c.id_contact
         left join Chat ch_last on ch_last.id = (
                            SELECT ch.id FROM chat ch where ch.id_conversation = c.id order by ch.sent_at DESC LIMIT 1\s
                        )
        where ct.id_workspace = :idWorkspace 
            AND c.status = :statusConv
            AND (
                CAST(COALESCE(ch_last.sent_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:dateStart, :sentinel) AS timestamp)
            )
            AND (
                CAST(COALESCE(ch_last.sent_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:dateEnd, :tomorow) AS timestamp)
            )
            AND (:unread IS NULL OR :unread = false OR c.unread_message_count > 0)
            AND (:agentId IS NULL OR c.handled_by = :agentId)
            AND (:orderStatus IS NULL OR c.id IN (SELECT ord.id_conversation from public.order ord where ord.status = :orderStatus))
            AND (
                    :keyword IS NULL\s
                    OR c.id IN (
                        SELECT msg.id_conversation
                        FROM chat msg
                        WHERE msg.pesan ILIKE CONCAT('%', :keyword, '%')
                    )
                  )
        """,
            countQuery = """

                SELECT COUNT(DISTINCT c.id)
            FROM public.conversation c
            JOIN contact ct ON ct.id = c.id_contact
                        left join Chat ch_last on ch_last.id = (
                            SELECT ch.id FROM chat ch where ch.id_conversation = c.id order by ch.sent_at DESC LIMIT 1\s
                        )
            WHERE ct.id_workspace = :idWorkspace
              AND c.status = :statusConv
            AND (
                CAST(COALESCE(ch_last.sent_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:dateStart, :sentinel) AS timestamp)
            )
            AND (
                CAST(COALESCE(ch_last.sent_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:dateEnd, :tomorow) AS timestamp)
            )
            AND (:unread IS NULL OR :unread = false OR c.unread_message_count > 0)
            AND (:agentId IS NULL OR c.handled_by = :agentId)
            AND (:orderStatus IS NULL OR c.id IN (SELECT ord.id_conversation from public.order ord where ord.status = :orderStatus))
                    AND (
                    :keyword IS NULL\s
                    OR c.id IN (
                        SELECT msg.id_conversation
                        FROM chat msg
                        WHERE msg.pesan ILIKE CONCAT('%', :keyword, '%')
                    )
                  )
                """,
            nativeQuery = true)
    Page<ConversationDto> getConversation(@Param("idWorkspace") Long idWorkspace,
                                          @Param("statusConv") String statusConv,
                                          @Param("dateStart") LocalDateTime dateStart,
                                          @Param("dateEnd")LocalDateTime  dateEnd,
                                          @Param("sentinel")LocalDateTime  sentinel,
                                          @Param("tomorow")LocalDateTime  tomorow,
                                          @Param("unread")Boolean unread,
                                          @Param("agentId" )Long agentId,
                                          @Param("orderStatus")String orderStatus,
                                          @Param("keyword") String keyword,
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