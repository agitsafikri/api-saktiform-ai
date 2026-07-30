package com.saktiform.api.model.product.formconfig;

/**
 * Kategori field pada konfigurasi form produk.
 *
 * <p>{@code SYSTEM} = enam field bawaan yang selalu ada pada setiap produk dan nilainya
 * disimpan pada kolom bertipe kuat di tabel {@code order}.
 * {@code CUSTOM} = field tambahan bebas per produk, nilainya disimpan pada
 * {@code order_custom_field}.
 */
public enum FieldCategory {
    SYSTEM,
    CUSTOM
}
