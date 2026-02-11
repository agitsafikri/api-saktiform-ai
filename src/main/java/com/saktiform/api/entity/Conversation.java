package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "conversation")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_contact", insertable = false, updatable = false)
    private Contact contact;

    @Column(name = "id_contact")
    private Long idContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by", insertable = false, updatable = false)
    private Account handledByAccount;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "status", length = Integer.MAX_VALUE)
    private String status;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "is_unread", columnDefinition = "boolean default false")
    private Boolean isUnread;

    @Column(name = "source", length = Integer.MAX_VALUE)
    private String source;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "handle_by_bot")
    private Boolean handleByBot;

    @Column(name = "active_order")
    private UUID activeOrderId;

    @Column(name = "unread_message_count", columnDefinition = "int default 0")
    private Integer unreadMessageCount;
}