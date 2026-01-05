package com.saktiform.api.model.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SaveProdukRequest {
    @Schema(description = "Data produk json")
    AddProdukDto data;

    @Schema(type = "string", format = "binary", description = "File gambar produk")
    private List<MultipartFile> gambarProduk;

    @Schema(type = "string", format = "binary", description = "File gambar produk")
    private List<MultipartFile> gambarTestimoni;
}
