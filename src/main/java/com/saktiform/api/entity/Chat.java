package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat")
public class Chat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conversation", insertable = false, updatable = false)
    private Conversation conversation;

    @Column(name = "id_conversation")
    private UUID idConversation;

    @Column(name = "type", length = Integer.MAX_VALUE)
    private String type;

    @Column(name = "pengirim", length = Integer.MAX_VALUE)
    private String pengirim;

    @Column(name = "pesan", length = Integer.MAX_VALUE)
    private String pesan;

    @Column(name = "media", length = Integer.MAX_VALUE)
    private String media;

    @Column(name = "status", length = Integer.MAX_VALUE)
    private String status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "message_id", length = Integer.MAX_VALUE)
    private String messageId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replied_to_id")
    private Chat repliedTo;



}