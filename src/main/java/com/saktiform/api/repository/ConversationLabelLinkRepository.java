package com.saktiform.api.repository;

import com.saktiform.api.entity.ConversationLabel;
import com.saktiform.api.entity.ConversationLabelLink;
import com.saktiform.api.model.label.response.ConversationLabelProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConversationLabelLinkRepository extends JpaRepository<ConversationLabelLink, Long> {

    boolean existsByConversationIdAndLabelId(UUID conversationId, Long labelId);

    /** Unassign — idempotent (deleteBy tidak error bila tidak ada baris). */
    @Modifying
    void deleteByConversationIdAndLabelId(UUID conversationId, Long labelId);

    /** Cascade hapus master label (dikelola di service). */
    @Modifying
    void deleteByLabelId(Long labelId);

    /** Label pada satu conversation (JOIN ke master) — untuk detail. */
    @Query("""
            SELECT l FROM ConversationLabel l
             WHERE l.id IN (SELECT k.labelId FROM ConversationLabelLink k
                             WHERE k.conversationId = :conversationId)
             ORDER BY l.name ASC
            """)
    List<ConversationLabel> findLabelsByConversationId(@Param("conversationId") UUID conversationId);

    /** Batch fetch untuk list (anti N+1): pasangan conversationId + field label. */
    @Query("""
            SELECT k.conversationId AS conversationId,
                   l.id AS id, l.name AS name, l.colorHex AS colorHex
              FROM ConversationLabelLink k
              JOIN ConversationLabel l ON l.id = k.labelId
             WHERE k.conversationId IN (:conversationIds)
             ORDER BY l.name ASC
            """)
    List<ConversationLabelProjection> findLabelsByConversationIds(
            @Param("conversationIds") Collection<UUID> conversationIds);
}
