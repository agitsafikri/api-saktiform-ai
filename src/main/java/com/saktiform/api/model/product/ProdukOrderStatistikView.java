package com.saktiform.api.model.product;

import java.util.UUID;

/**
 * Statistik order per produk untuk rentang tanggal order masuk
 * ({@link com.saktiform.api.repository.ProdukRepository#getOrderStatistikByProduk}).
 */
public interface ProdukOrderStatistikView {
    UUID getIdProduk();

    /** Order masuk dalam rentang, kecuali berstatus DELETE. */
    Long getTotalOrder();

    /** Order masuk dalam rentang yang saat ini berstatus PAID. */
    Long getTotalDibayar();
}
