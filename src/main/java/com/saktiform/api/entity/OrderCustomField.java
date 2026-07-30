package com.saktiform.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Nilai satu Custom Field pada sebuah order, beserta <b>snapshot</b> definisinya.
 *
 * <p>Kolom {@code field_key}, {@code field_label}, {@code field_type}, dan
 * {@code sort_order} disalin dari konfigurasi produk pada saat order dibuat. Dengan
 * begitu, perubahan konfigurasi di kemudian hari tidak mengubah makna order lama:
 * order tetap menampilkan label sebagaimana yang dilihat pelanggan saat memesan.
 *
 * <p><b>Tidak ada foreign key ke {@code produk_form_config.id}.</b> Id baris konfigurasi
 * tidak stabil, dan Custom Field yang belum dipakai boleh dihapus permanen. Keterhubungan
 * dipelihara secara logis melalui pasangan {@code (id_produk, field_key)} yang dijamin
 * stabil, dilengkapi snapshot yang membuat baris ini tetap terbaca penuh bahkan ketika
 * konfigurasi asalnya sudah tidak ada.
 *
 * <p>{@code id_produk} didenormalisasi agar perhitungan {@code usageCount} — dipanggil
 * setiap kali layar konfigurasi dibuka — dapat dijawab tanpa join ke tabel {@code order}.
 */
@Getter
@Setter
@Entity
@Table(name = "order_custom_field",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ocf_order_field",
                columnNames = {"id_order", "field_key"}),
        indexes = {
                @Index(name = "idx_ocf_order", columnList = "id_order, sort_order"),
                @Index(name = "idx_ocf_produk_field", columnList = "id_produk, field_key")
        })
public class OrderCustomField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "id_order", nullable = false)
    private UUID idOrder;

    @Column(name = "id_produk", nullable = false)
    private UUID idProduk;

    /** Snapshot field key pada saat order dibuat. */
    @Column(name = "field_key", length = 64, nullable = false)
    private String fieldKey;

    /** Snapshot label pada saat order dibuat. */
    @Column(name = "field_label", length = Integer.MAX_VALUE, nullable = false)
    private String fieldLabel;

    /** Snapshot tipe field pada saat order dibuat. */
    @Column(name = "field_type", length = 32, nullable = false)
    private String fieldType;

    @Column(name = "field_value", length = Integer.MAX_VALUE)
    private String fieldValue;

    /** Snapshot urutan tampil, agar detail order mencerminkan urutan form saat pemesanan. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
