package com.saktiform.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "produk")
public class Produk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "nama_produk", nullable = false, length = Integer.MAX_VALUE)
    private String namaProduk;

    @NotNull
    @Column(name = "url_checkout", nullable = false, length = Integer.MAX_VALUE)
    private String urlCheckout;

    @Column(name = "narasi_tombol", length = Integer.MAX_VALUE)
    private String narasiTombol;

    @Column(name = "warna_tombol", length = Integer.MAX_VALUE)
    private String warnaTombol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_gudang", insertable = false, updatable = false)
    private Gudang gudang;

    @Column(name = "id_gudang")
    private Long idGudang;

    @Column(name = "embeded_checkout_script", length = Integer.MAX_VALUE)
    private String embededCheckoutScript;

    @Column(name = "embeded_purchase_script", length = Integer.MAX_VALUE)
    private String embededPurchaseScript;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "google_gtm_id", insertable = false, updatable = false)
    private ProdukIklan produkIklan_GoogleGtm;

    @Column(name = "google_gtm_id")
    private Long googleGtm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facebook_pixel_id", insertable = false, updatable = false)
    private ProdukIklan produkIklan_FacebookPixel;

    @Column(name = "facebook_pixel_id")
    private Long facebookPixel;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_workspace",  insertable = false, updatable = false)
    private Workspace workspace;

    @Column(name = "id_workspace")
    private Long idWorkspace;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name="sold_count")
    private Long soldCount;

    @Column(name="order_count")
    private Long orderCount;

}