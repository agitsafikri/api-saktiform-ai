package com.saktiform.api.repository;

import com.saktiform.api.entity.ProdukFormConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProdukFormConfigRepository extends JpaRepository<ProdukFormConfig, Long> {
    void deleteProdukFormConfigByIdProduk(UUID idProduk);

    List<ProdukFormConfig> getProdukFormConfigsByIdProduk(UUID idProduk);
}