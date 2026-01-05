package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "produk_form_config")
public class ProdukFormConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_produk", insertable = false, updatable = false)
    private Produk produk;

    @Column(name = "id_produk")
    private UUID idProduk;

    @Column(name = "tipe_field", length = Integer.MAX_VALUE)
    private String tipeField;

    @Column(name = "label", length = Integer.MAX_VALUE)
    private String label;

    @Column(name = "placeholder", length = Integer.MAX_VALUE)
    private String placeholder;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "orders")
    Integer order;

    @Column(name = "is_mandatory")
    Boolean isMandatory;

}