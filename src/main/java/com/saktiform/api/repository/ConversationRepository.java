package com.saktiform.api.repository;

import com.saktiform.api.entity.Conversation;
import com.saktiform.api.model.chat.ConversationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
                            c.status
        FROM public.conversation c
        JOIN contact ct ON ct.id = c.id_contact
         left join Chat ch_last on ch_last.id = (
                            SELECT ch.id FROM chat ch where ch.id_conversation = c.id order by ch.sent_at DESC LIMIT 1\s
                        )
        where ct.id_workspace = :idWorkspace AND c.status = :statusConv
        """,
            countQuery = """

                SELECT COUNT(DISTINCT c.id)
            FROM public.conversation c
            JOIN contact ct ON ct.id = c.id_contact
            WHERE ct.id_workspace = :idWorkspace
              AND c.status = :statusConv
                """,
            nativeQuery = true)
    Page<ConversationDto> getConversation(@Param("idWorkspace") Long idWorkspace, @Param("statusConv") String statusConv, Pageable pageable);
}