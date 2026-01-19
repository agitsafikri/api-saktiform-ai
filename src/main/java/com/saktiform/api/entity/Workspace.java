package com.saktiform.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "workspace")
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waba_id", insertable = false, updatable = false)
    private WhatsappBusinessApi waba;

    @Column(name = "waba_id")
    private UUID wabaId;

    @NotNull
    @Column(name = "nama_workspace", nullable = false, length = Integer.MAX_VALUE)
    private String namaWorkspace;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_domain", insertable = false, updatable = false)
    private Domain domain;

    @Column(name = "id_domain")
    private Long idDomain;

    @ManyToMany(mappedBy = "workspaces")
    private Set<Account> accounts = new HashSet<>();

}