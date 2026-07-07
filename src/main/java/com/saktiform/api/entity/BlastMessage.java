package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Recipient final + status pengiriman per orang. Menyimpan snapshot pesan keluar
 * dan balasan pertama user (first_reply_*) untuk Report (FR-13, FR-14, BR-23).
 */
@Getter
@Setter
@Entity
@Table(name = "blast_message",
        uniqueConstraints = @UniqueConstraint(name = "uq_message_campaign_phone",
                columnNames = {"campaign_id", "phone"}),
        indexes = {
                @Index(name = "idx_message_campaign_status", columnList = "campaign_id, status"),
                @Index(name = "idx_message_provider_msgid", columnList = "provider_message_id"),
                @Index(name = "idx_message_ws_phone", columnList = "id_workspace, phone")
        })
public class BlastMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_workspace", nullable = false)
    private Long idWorkspace;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "contact_id")
    private Long contactId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "name", length = Integer.MAX_VALUE)
    private String name;

    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @Column(name = "rendered_message", length = Integer.MAX_VALUE)
    private String renderedMessage;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "waiting_at")
    private Instant waitingAt;

    @Column(name = "sending_at")
    private Instant sendingAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    // First reply (balasan pertama user) — denormalized untuk Report (FR-13, BR-23)
    @Column(name = "first_reply_chat_id")
    private UUID firstReplyChatId;

    @Column(name = "first_reply_message", length = Integer.MAX_VALUE)
    private String firstReplyMessage;

    @Column(name = "first_reply_media_type", length = 16)
    private String firstReplyMediaType;

    @Column(name = "first_reply_media_link", length = Integer.MAX_VALUE)
    private String firstReplyMediaLink;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
