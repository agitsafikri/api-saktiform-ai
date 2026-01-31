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

    ProdukListDto(UUID id, String namaProduk, Number harga, Long totalOrder, Long totalDibayar){
        this.id = id;
        this.namaProduk = namaProduk;
        this.harga = harga != null ? harga.longValue() : null;;
        this.totalOrder = totalOrder;
        this.totalDibayar = totalDibayar;
        if(totalDibayar == 0){
            this.rasioDibayar = "0%";
        }else {
            this.rasioDibayar =  String.format("%.2f%%", ((double)totalDibayar / (double)totalOrder) * 100);
        }

        this.totalTerjual = totalDibayar;
    }

}