package com.saktiform.api.model.product;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link com.saktiform.api.entity.Produk}
 */

@Getter
@Setter
@NoArgsConstructor
public class ProdukListDto implements Serializable {
    UUID id;
    String namaProduk;
    Long harga;
    Long totalOrder;
    Long totalDibayar;
    String rasioDibayar;
    Long totalTerjual;
    String gambarProduk;
    String urlCheckout;

    ProdukListDto(UUID id, String namaProduk, Number harga, Long totalOrder, Long totalDibayar, String urlCheckout){
        this.id = id;
        this.namaProduk = namaProduk;
        this.harga = harga != null ? harga.longValue() : null;
        this.urlCheckout = urlCheckout;
        applyStatistik(totalOrder, totalDibayar);
    }

    /**
     * Mengisi totalOrder, totalDibayar, totalTerjual, dan rasioDibayar sekaligus.
     * Dipakai constructor (counter produk) dan saat statistik dihitung ulang untuk
     * rentang tanggal order masuk. Nilai null (counter produk lama) diperlakukan sebagai 0.
     */
    public void applyStatistik(Long totalOrder, Long totalDibayar) {
        long order = totalOrder == null ? 0L : totalOrder;
        long dibayar = totalDibayar == null ? 0L : totalDibayar;

        this.totalOrder = order;
        this.totalDibayar = dibayar;
        this.totalTerjual = dibayar;
        this.rasioDibayar = (order == 0 || dibayar == 0)
                ? "0%"
                : String.format("%.2f%%", ((double) dibayar / (double) order) * 100);
    }

}