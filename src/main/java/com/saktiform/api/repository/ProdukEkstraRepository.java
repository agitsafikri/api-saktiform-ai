package com.saktiform.api.repository;

import com.saktiform.api.entity.ProdukEkstra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProdukEkstraRepository extends JpaRepository<ProdukEkstra, Long> {
    void deleteProdukEkstrasByIdProduk(UUID idProduk);

    List<ProdukEkstra> getProdukEkstrasByIdProduk(UUID idProduk);
}