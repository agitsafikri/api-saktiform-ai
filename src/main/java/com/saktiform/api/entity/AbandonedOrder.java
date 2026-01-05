package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"abandon_order\"")
public class AbandonedOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_produk", insertable = false, updatable = false)
    private Produk produk;

    @Column(name = "id_produk")
    private UUID idProduk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_atribut_produk", insertable = false, updatable = false)
    private AtributProduk atributProduk;

    @Column(name = "id_atribut_produk")
    private UUID idAtributProduk;

    @Column(name = "nama_penerima", length = Integer.MAX_VALUE)
    private String namaPenerima;

    @Column(name = "nomor_whatsapp", length = Integer.MAX_VALUE)
    private String nomorWhatsapp;

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
    @JoinColumn(name = "id_pembayaran", insertable = false, updatable = false)
    private ProdukPembayaran pembayaranObject;

    @Column(name = "id_pembayaran")
    private Long idPembayaran;

    @Column(name = "pembayaran")
    private String pembayaran;

    @Column(name = "ongkos_kirim")
    private Long ongkosKirim;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "config_pembayaran")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> configPembayaran;

    @Column(name = "deskripsi_produk", length = Integer.MAX_VALUE)
    private String deskripsiProduk;

    @Column(name = "harga")
    private Long harga;

    @Column(name = "berat")
    private Integer berat;



}