package com.saktiform.api.model.Order;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AttributProdukOrder {
    private UUID id;
    private String deskripsi;
    private Long harga;
}
