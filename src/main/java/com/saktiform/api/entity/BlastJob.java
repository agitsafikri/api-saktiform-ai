package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Unit kerja queue. 1 attempt pengiriman = 1 job. Diklaim worker via FOR UPDATE SKIP LOCKED.
 * Partial index klaim (WHERE status='READY') dibuat BlastSchemaInitializer (post-startup).
 */
@Getter
@Setter
@Entity
@Table(name = "blast_job",
        uniqueConstraints = @UniqueConstraint(name = "uq_job_message_attempt",
                columnNames = {"message_id", "attempt"}),
        indexes = {
                @Index(name = "idx_job_campaign", columnList = "campaign_id, status"),
                @Index(name = "idx_job_lease", columnList = "status, locked_until")
        })
public class BlastJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "attempt", nullable = false)
    private Integer attempt;

    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    @Column(name = "priority")
    private Short priority = 0;

    @Column(name = "dedup_key", length = 128, nullable = false)
    private String dedupKey;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "locked_by", length = 64)
    private String lockedBy;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
