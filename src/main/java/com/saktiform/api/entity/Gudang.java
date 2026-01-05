package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "gudang")
public class Gudang {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "nama_gudang", length = Integer.MAX_VALUE)
    private String namaGudang;

    @Column(name = "alamat", length = Integer.MAX_VALUE)
    private String alamat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_provinsi", insertable = false, updatable = false)
    private Province provinsi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_kota", insertable = false, updatable = false)
    private City kota;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_kecamatan", insertable = false, updatable = false)
    private District kecamatan;

    @Column(name = "id_provinsi")
    private Integer idProvinsi;

    @Column(name = "id_kota")
    private Integer idKota;

    @Column(name = "id_kecamatan")
    private Integer idKecamatan;

    @Column(name = "status", length = Integer.MAX_VALUE)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_workspace", insertable = false, updatable = false)
    private Workspace workspace;

    @Column(name = "id_workspace")
    private Long idWorkspace;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

}