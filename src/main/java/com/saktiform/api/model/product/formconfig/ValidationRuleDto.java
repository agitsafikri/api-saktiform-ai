package com.saktiform.api.model.product.formconfig;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Aturan validasi tambahan sebuah field, disimpan pada kolom
 * {@code produk_form_config.validation_rule} ({@code jsonb}).
 *
 * <p>Dengan cakupan tipe TEXT / TEXTAREA / SELECT, hanya tiga aturan yang relevan.
 * Kolom tetap berbentuk {@code jsonb} sehingga penambahan aturan baru di kemudian hari
 * tidak memerlukan perubahan skema.
 *
 * <p>{@code pattern} tidak diekspos pada UI dashboard (pengguna bukan pengembang) namun
 * tetap diterima API untuk keperluan integrasi. Nilainya divalidasi terhadap ReDoS.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValidationRuleDto implements Serializable {

    private Integer minLength;
    private Integer maxLength;
    private String pattern;

    public static ValidationRuleDto ofLength(Integer minLength, Integer maxLength) {
        return new ValidationRuleDto(minLength, maxLength, null);
    }

    public static ValidationRuleDto ofPattern(String pattern) {
        return new ValidationRuleDto(null, null, pattern);
    }

    /**
     * {@code true} bila seluruh atribut kosong — dipakai untuk menyimpan {@code null}
     * alih-alih objek JSON hampa.
     *
     * <p>{@code @JsonIgnore} wajib: tanpa itu Jackson memperlakukan method ini sebagai
     * properti bean bernama {@code "empty"} dan menuliskannya ke kolom {@code jsonb},
     * lalu gagal saat membacanya kembali menjadi objek ini.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return minLength == null && maxLength == null
                && (pattern == null || pattern.isBlank());
    }
}
