package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Join many-to-many antara conversation dan {@link ConversationLabel}.
 *
 * <p>{@code id_workspace} di-denormalisasi karena {@code Conversation} tidak punya kolom workspace
 * (tenant di-resolve lewat {@code conversation.contact.id_workspace}); denormalisasi ini mempercepat
 * filter/guard tanpa join contact. Unique {@code (conversation_id, label_id)} menjaga idempotency assign.
 */
@Getter
@Setter
@Entity
@Table(name = "conversation_label_link",
        uniqueConstraints = @UniqueConstraint(name = "uq_link_conversation_label",
                columnNames = {"conversation_id", "label_id"}),
        indexes = {
                @Index(name = "idx_link_conversation", columnList = "conversation_id"),
                @Index(name = "idx_link_label", columnList = "label_id"),
                @Index(name = "idx_link_ws", columnList = "id_workspace")
        })
public class ConversationLabelLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "created_at")
    private Instant createdAt;
}
