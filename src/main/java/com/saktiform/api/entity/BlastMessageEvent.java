package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Append-only timeline setiap perubahan status pesan (audit & debugging). Tidak pernah di-update.
 */
@Getter
@Setter
@Entity
@Table(name = "blast_message_event",
        indexes = @Index(name = "idx_event_message", columnList = "message_id, created_at"))
public class BlastMessageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16, nullable = false)
    private String toStatus;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "attempt")
    private Integer attempt;

    @Column(name = "detail", length = 512)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
