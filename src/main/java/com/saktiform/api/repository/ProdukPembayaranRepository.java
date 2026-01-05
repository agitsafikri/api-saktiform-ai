package com.saktiform.api.repository;


import com.saktiform.api.entity.ProdukPembayaran;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProdukPembayaranRepository extends JpaRepository<ProdukPembayaran, Long> {
    Object findProdukPembayaranByPembayaranAndIdProduk(String pembayaran, UUID idProduk);

    List<ProdukPembayaran> getProdukPembayaransByIdProduk(UUID idProduk);

    void deleteAllByIdProduk(UUID idProduk);

    ProdukPembayaran findByIdProdukAndPembayaran(UUID idProduk, String pembayaran);


    @Query("SELECT p.pembayaran FROM ProdukPembayaran p WHERE p.idProduk = :idProduk")
    List<String> getListPembayaranProduk(@Param("idProduk") UUID idProduk);
}