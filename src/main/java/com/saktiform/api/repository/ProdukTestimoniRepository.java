package com.saktiform.api.repository;

import com.saktiform.api.entity.ProdukTestimoni;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProdukTestimoniRepository extends JpaRepository<ProdukTestimoni, Long> {
    List<ProdukTestimoni> findProdukTestimoniById(Long id);

    void deleteProdukTestimoniByIdProduk(UUID idProduk);

    List<ProdukTestimoni> getProdukTestimoniByIdProduk(UUID idProduk);
}