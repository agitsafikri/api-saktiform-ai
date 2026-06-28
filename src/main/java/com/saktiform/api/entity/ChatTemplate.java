package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "chat_template")
public class ChatTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_waba", insertable = false, updatable = false)
    private WhatsappBusinessApi waba;

    @Column(name = "id_waba")
    private UUID wabaId;

    @Column(name = "status", length = Integer.MAX_VALUE)
    private String status;

    @Column(name = "nama_template", length = Integer.MAX_VALUE)
    private String namaTemplate;

    @Column(name = "content", length = Integer.MAX_VALUE)
    private String content;

    @Column(name = "category", length = Integer.MAX_VALUE)
    private String category;

    @Column(name = "media_link", length = Integer.MAX_VALUE)
    private String mediaLink;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_waba", insertable = false, updatable = false)
    private Workspace workspace;

    @Column(name = "id_workspace")
    private Long idWorkspace;

}