package com.saktiform.api.entity;

import com.saktiform.api.model.product.formconfig.FieldCategory;
import com.saktiform.api.model.product.formconfig.FormFieldType;
import com.saktiform.api.model.product.formconfig.OptionDto;
import com.saktiform.api.model.product.formconfig.ValidationRuleDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Konfigurasi satu field pada form checkout sebuah produk.
 *
 * <p><b>Catatan nama kolom.</b> Tiga kolom mempertahankan nama fisik lamanya
 * ({@code orders}, {@code is_mandatory}, {@code tipe_field}) karena
 * {@code spring.jpa.hibernate.ddl-auto=update} tidak dapat mengganti nama kolom.
 * Atribut Java memakai penamaan yang bersih dan dipetakan lewat {@code @Column(name=…)},
 * sehingga seluruh kode aplikasi dan kontrak API tidak terpengaruh.
 *
 * <p><b>Catatan tipe {@code tipeField}.</b> Kolom tetap dipetakan sebagai {@code String},
 * bukan {@code @Enumerated}. Basis data existing memuat nilai legacy yang belum
 * ternormalisasi (mis. {@code "text"} huruf kecil) dan pemetaan enum akan melempar
 * {@code IllegalArgumentException} saat membaca baris tersebut — sebelum migrasi sempat
 * berjalan. Konversi ke {@link FormFieldType} dilakukan lewat {@link #getFieldType()}
 * yang toleran, sehingga tidak ada ketergantungan urutan deploy terhadap migrasi.
 */
@Getter
@Setter
@Entity
@Table(name = "produk_form_config",
        indexes = {
                @Index(name = "idx_pfc_produk_active_sort", columnList = "id_produk, is_active, orders"),
                @Index(name = "idx_pfc_category", columnList = "field_category")
        })
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

    /** Identitas stabil field — kontrak antara frontend, validator, dan snapshot order. */
    @Column(name = "field_key", length = 64)
    private String fieldKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_category", length = 16)
    private FieldCategory fieldCategory;

    /**
     * Tipe field. Nama kolom fisik dipertahankan ({@code tipe_field}), demikian pula
     * panjangnya ({@code text}) agar {@code ddl-auto=update} tidak mencoba menyempitkan
     * tipe kolom. Akses bertipe kuat lewat {@link #getFieldType()}.
     */
    @Column(name = "tipe_field", length = Integer.MAX_VALUE)
    private String tipeField;

    @Column(name = "label", length = Integer.MAX_VALUE)
    private String label;

    @Column(name = "placeholder", length = Integer.MAX_VALUE)
    private String placeholder;

    @Column(name = "help_text", length = Integer.MAX_VALUE)
    private String helpText;

    /** Nama kolom fisik dipertahankan: {@code is_mandatory}. */
    @Column(name = "is_mandatory")
    private Boolean isRequired;

    @Column(name = "is_active")
    private Boolean isActive;

    /** Nama kolom fisik dipertahankan: {@code orders}. */
    @Column(name = "orders")
    private Integer sortOrder;

    /** Daftar pilihan untuk tipe SELECT. {@code null} untuk tipe lain. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options")
    private List<OptionDto> options;

    @Column(name = "default_value", length = Integer.MAX_VALUE)
    private String defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_rule")
    private ValidationRuleDto validationRule;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ── Akses bertipe kuat atas tipe_field ──

    /**
     * Tipe field sebagai enum, toleran terhadap nilai legacy.
     *
     * @return {@link FormFieldType#TEXT} bila kolom kosong atau tidak dikenal — tipe
     *         paling permisif, sehingga data yang sudah terkumpul tidak pernah ditolak
     */
    @Transient
    public FormFieldType getFieldType() {
        FormFieldType parsed = FormFieldType.parseStrict(tipeField);
        if (parsed != null) {
            return parsed;
        }
        FormFieldType legacy = FormFieldType.parseLegacy(tipeField);
        return legacy != null ? legacy : FormFieldType.TEXT;
    }

    public void setFieldType(FormFieldType fieldType) {
        this.tipeField = fieldType == null ? null : fieldType.name();
    }

    @Transient
    public boolean isSystem() {
        return fieldCategory == FieldCategory.SYSTEM;
    }

    @Transient
    public boolean isCustom() {
        return fieldCategory == FieldCategory.CUSTOM;
    }
}
