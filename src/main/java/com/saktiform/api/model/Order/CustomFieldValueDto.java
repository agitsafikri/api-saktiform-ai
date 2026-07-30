package com.saktiform.api.model.Order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Satu nilai Custom Field pada payload {@code POST /order/create}.
 *
 * <p>Klien hanya boleh mengirim {@code fieldKey} dan {@code value}. Metadata konfigurasi
 * (tipe, wajib/tidak, daftar pilihan) <b>tidak</b> diterima dari klien — server selalu
 * membacanya ulang dari basis data. Endpoint ini publik; klien tidak boleh menjadi
 * otoritas validasi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomFieldValueDto implements Serializable {

    @NotBlank(message = "Field key wajib diisi.")
    @Size(max = 64, message = "Field key maksimum 64 karakter.")
    private String fieldKey;

    /** Nilai mentah. Divalidasi dan dinormalisasi terhadap tipe field dari basis data. */
    private Object value;
}
