package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Definisi campaign + snapshot pesan + counter progress + status (state machine).
 */
@Getter
@Setter
@Entity
@Table(name = "blast_campaign",
        indexes = {
                @Index(name = "idx_campaign_ws_status", columnList = "id_workspace, status, created_at"),
                @Index(name = "idx_campaign_ws_name", columnList = "id_workspace, name")
        })
public class BlastCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "import_id")
    private Long importId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "message_source", length = 16)
    private String messageSource;

    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    @Column(name = "message_content", length = Integer.MAX_VALUE)
    private String messageContent;

    @Column(name = "media_link", length = Integer.MAX_VALUE)
    private String mediaLink;

    @Column(name = "target_type", length = 16)
    private String targetType;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "batch_size")
    private Integer batchSize;

    @Column(name = "delay_ms")
    private Integer delayMs;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "total_recipient")
    private Integer totalRecipient = 0;

    @Column(name = "count_waiting")
    private Integer countWaiting = 0;

    @Column(name = "count_sending")
    private Integer countSending = 0;

    @Column(name = "count_sent")
    private Integer countSent = 0;

    @Column(name = "count_failed")
    private Integer countFailed = 0;

    @Column(name = "count_replied")
    private Integer countReplied = 0;

    @Column(name = "count_skipped")
    private Integer countSkipped = 0;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
