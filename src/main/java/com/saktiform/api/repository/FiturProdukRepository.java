package com.saktiform.api.repository;

import com.saktiform.api.entity.FiturProduk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FiturProdukRepository extends JpaRepository<FiturProduk, Long> {
    void deleteAllByIdProduk(UUID idProduk);

    List<FiturProduk> getFiturProduksByIdProduk(UUID idProduk);
}