package com.saktiform.api.model.Order;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Filter multi-nilai untuk {@code GET /order} dan {@code GET /order/export}
 * (docs/prd/order-multi-filter.md, docs/prd/order-product-filter.md). Set kosong berarti
 * filter tidak diterapkan.
 *
 * <p>Dibentuk lewat {@link com.saktiform.api.service.order.OrderListFilterParser}, sehingga
 * isinya selalu sudah tervalidasi: ID lokasi berupa bilangan bulat positif, ID produk berupa
 * UUID, status dan jenis pembayaran berupa nama enum UPPERCASE.
 */
public record OrderListFilter(
        Set<Integer> idProvinsi,
        Set<Integer> idKota,
        Set<Integer> idKecamatan,
        Set<String> status,
        Set<String> jenisPembayaran,
        Set<UUID> idProduk
) {

    public OrderListFilter {
        idProvinsi = Set.copyOf(idProvinsi);
        idKota = Set.copyOf(idKota);
        idKecamatan = Set.copyOf(idKecamatan);
        status = Set.copyOf(status);
        jenisPembayaran = Set.copyOf(jenisPembayaran);
        idProduk = Set.copyOf(idProduk);
    }

    /** Filter tanpa batasan apa pun. */
    public static OrderListFilter none() {
        return new OrderListFilter(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    // Bentuk string koma untuk parameter native query ('' = tanpa filter).

    public String idProvinsiCsv() {
        return csv(idProvinsi);
    }

    public String idKotaCsv() {
        return csv(idKota);
    }

    public String idKecamatanCsv() {
        return csv(idKecamatan);
    }

    public String statusCsv() {
        return csv(status);
    }

    public String jenisPembayaranCsv() {
        return csv(jenisPembayaran);
    }

    public String idProdukCsv() {
        return csv(idProduk);
    }

    private static String csv(Collection<?> values) {
        return values.stream().map(String::valueOf).sorted().collect(Collectors.joining(","));
    }
}
