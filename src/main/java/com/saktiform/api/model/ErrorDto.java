package com.saktiform.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Satu galat validasi.
 *
 * <p>{@code field} memakai <b>field key</b> agar frontend dapat memetakannya ke input
 * secara terprogram; {@code message} memakai <b>label</b> yang dikenal pengguna.
 * {@code code} adalah konstanta stabil bagian dari kontrak API — frontend bercabang
 * atas kode, bukan atas teks pesan.
 *
 * <p>{@code @AllArgsConstructor} sengaja tidak dipakai lagi: konstruktor dua argumen
 * wajib dipertahankan apa adanya karena dipanggil {@code MapperHelper.getErrors()}.
 * {@code @JsonInclude(NON_NULL)} menjaga respons galat existing tetap identik bagi
 * klien lama (tidak memunculkan {@code "code": null}).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDto {

    private String field;
    private String message;
    private String code;
    private Map<String, Object> meta;

    public ErrorDto(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public ErrorDto(String field, String message, String code, Map<String, Object> meta) {
        this.field = field;
        this.message = message;
        this.code = code;
        this.meta = meta;
    }
}
