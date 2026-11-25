package com.saktiform.api.repository;

import com.saktiform.api.entity.GambarProduk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GambarProdukRepository extends JpaRepository<GambarProduk, Long> {
    void deleteGambarProdukByIdProduk(UUID idProduk);


    List <GambarProduk> findGambarProduksByIdProduk(UUID idProduk);

    /** Batch untuk list produk (anti N+1): gambar beberapa produk sekaligus, urut id. */
    List<GambarProduk> findByIdProdukInOrderByIdAsc(Collection<UUID> idProduk);
}