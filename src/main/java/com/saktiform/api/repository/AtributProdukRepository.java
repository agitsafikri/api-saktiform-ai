package com.saktiform.api.repository;

import com.saktiform.api.entity.AtributProduk;
import com.saktiform.api.model.product.AtributProdukDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface AtributProdukRepository extends JpaRepository<AtributProduk, UUID> {
    List<AtributProduk> getAtributProduksByIdProduk(UUID idProduk);

    void deleteAllByIdProduk(UUID idProduk);

    @Transactional
    @Modifying
    @Query("update AtributProduk a set a.isDeleted = ?1 where a.idProduk = ?2")
    int updateIsDeletedByIdProduk(Boolean isDeleted, UUID idProduk);

    List<AtributProdukDto> getAtributProduksByIdProdukAndIsDeleted(UUID idProduk, Boolean isDeleted);

    @Query("SELECT new com.saktiform.api.model.product.AtributProdukDto(a.id, a.deskripsi, a.harga, a.berat) FROM AtributProduk a WHERE a.idProduk = :idProduk AND a.isDeleted != TRUE")
    List<AtributProdukDto> getListAttributProduk(@Param("idProduk") UUID idProduk);
}