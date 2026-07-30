package com.saktiform.api.util;

import com.saktiform.api.model.ErrorDto;

import java.util.List;
import java.util.Map;

/**
 * Galat validasi domain yang membawa <b>seluruh</b> pelanggaran sekaligus.
 *
 * <p>Pola {@code catch (Exception e) -> e.getMessage()} yang dipakai controller existing
 * hanya dapat menyampaikan satu pesan. Exception ini menyimpan daftar {@link ErrorDto}
 * lengkap sehingga pengguna dapat memperbaiki seluruh kesalahan dalam satu putaran.
 *
 * <p>{@code super(...)} tetap diisi pesan galat pertama agar controller yang belum
 * menangani exception ini secara khusus tetap menghasilkan pesan yang bermakna.
 */
public class ValidationException extends RuntimeException {

    private final transient List<ErrorDto> errors;

    public ValidationException(List<ErrorDto> errors) {
        super(errors == null || errors.isEmpty() ? "Validation failed" : errors.get(0).getMessage());
        this.errors = errors == null ? List.of() : errors;
    }

    public ValidationException(ErrorDto error) {
        this(List.of(error));
    }

    public ValidationException(String field, String code, String message) {
        this(new ErrorDto(field, message, code, null));
    }

    public ValidationException(String field, String code, String message, Map<String, Object> meta) {
        this(new ErrorDto(field, message, code, meta));
    }

    public List<ErrorDto> getErrors() {
        return errors;
    }
}
