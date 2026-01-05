package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "quick_replies")
public class QuickReply {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "narasi", length = Integer.MAX_VALUE)
    private String narasi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_workspace", insertable = false, updatable = false)
    private Workspace workspace;

    @Column(name = "id_workspace")
    private Long idWorkspace;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}