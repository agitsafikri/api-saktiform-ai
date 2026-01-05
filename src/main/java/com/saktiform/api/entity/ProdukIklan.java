package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "produk_iklan")
public class ProdukIklan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "platform_iklan", length = Integer.MAX_VALUE)
    private String platformIklan;

    @Column(name = "id_iklan", length = Integer.MAX_VALUE)
    private String idIklan;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}