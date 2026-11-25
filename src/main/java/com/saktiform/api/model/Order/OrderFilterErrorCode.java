package com.saktiform.api.model.Order;

/**
 * Kode galat stabil untuk filter {@code GET /order} dan {@code GET /order/export}.
 * Bagian dari kontrak API: frontend bercabang atas kode ini, bukan atas teks pesan.
 * Lihat docs/prd/order-multi-filter.md §6.
 */
public final class OrderFilterErrorCode {

    /** Nilai bukan bilangan bulat positif (ID wilayah) atau bukan anggota enum. */
    public static final String INVALID_FILTER_VALUE = "INVALID_FILTER_VALUE";

    /** Jumlah nilai pada satu filter melebihi batas. */
    public static final String FILTER_TOO_MANY_VALUES = "FILTER_TOO_MANY_VALUES";

    /** Kombinasi filter lokasi bertingkat yang tidak diizinkan. */
    public static final String FILTER_LOCATION_CONFLICT = "FILTER_LOCATION_CONFLICT";

    private OrderFilterErrorCode() {
    }
}
