package com.saktiform.api.repository;

import com.saktiform.api.entity.ProdukIklan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdukIklanRepository extends JpaRepository<ProdukIklan, Long> {
    @Query("""
        Select pi.idIklan FROM ProdukIklan pi where pi.platformIklan = :platform AND pi.idIklan like %:idIklan%
    """)
    List<String> getListProdukIklanId(@Param("platform") String platrformIklan, @Param("idIklan") String idIklan);

    List<ProdukIklan> getProdukIklanByPlatformIklanAndIdIklan(String platformIklan, String idIklan);

    List<ProdukIklan> getProdukIklanById(Long id);
}