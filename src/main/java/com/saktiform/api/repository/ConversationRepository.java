package com.saktiform.api.repository;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.chat.ConversationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Conversation findByIdContact(Long idContact);

    /**
     * Filter bersama untuk getConversation (data + count) agar keduanya tidak bisa berbeda isi.
     * Mengharapkan alias {@code c} (conversation) dan {@code ct} (contact).
     * Subquery EXISTS ke order / chat bertumpu pada index order(id_conversation) dan
     * chat(id_conversation, sent_at).
     */
    String CONVERSATION_FILTER = """
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
                  :jenisPembayaran IS NULL
                  OR EXISTS (
                      SELECT 1
                      FROM public."order" ord
                      WHERE ord.id_conversation = c.id
                        AND ord.pembayaran = :jenisPembayaran
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
              )

              AND (
                  :labelFilter = false
                  OR EXISTS (
                      SELECT 1
                      FROM conversation_label_link ll
                      WHERE ll.conversation_id = c.id
                        AND ll.label_id IN (:labelIds)
                  )
              )
            """;

    // Tanpa ORDER BY: urutan (last_message_at DESC) diambil dari Sort di Pageable
    // (lihat ConversationService), supaya tidak terduplikasi.
    @Query(value = """
            SELECT
                c.id as id,
                ct.nama_kontak as contactName,
                c.last_message_type as lastMessageType,
                c.last_message as lastMessage,
                c.last_message_at as lastMessageTimeRaw,
                c.status as statusConversation,
                c.chat_status as chatStatus,
                c.status as status,
                c.unread_message_count as unreadMessageCount
            FROM public.conversation c
            JOIN contact ct ON ct.id = c.id_contact
            """ + CONVERSATION_FILTER,
            countQuery = """
            SELECT COUNT(c.id)
            FROM public.conversation c
            JOIN contact ct ON ct.id = c.id_contact
            """ + CONVERSATION_FILTER,
            nativeQuery = true)
    Page<ConversationDto> getConversation(@Param("idWorkspace") Long idWorkspace,
                                          @Param("statusConv") String statusConv,
                                          @Param("dateStart") LocalDateTime dateStart,
                                          @Param("dateEnd")LocalDateTime  dateEnd,
                                          @Param("unread")Boolean unread,
                                          @Param("agentId" )Long agentId,
                                          @Param("orderStatus")String orderStatus,
                                          @Param("keyword") String keyword,
                                          @Param("chatStatus") String chatStatus,
                                          @Param("labelFilter") boolean labelFilter,
                                          @Param("labelIds") Collection<Long> labelIds,
                                          @Param("jenisPembayaran") String jenisPembayaran,
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
