package com.saktiform.api.repository;

import com.saktiform.api.entity.Gudang;
import com.saktiform.api.model.gudang.GudangDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GudangRepository extends JpaRepository<Gudang, Long> {

    @Query("""
        Select new  com.saktiform.api.model.gudang.GudangDto(
                id, namaGudang, alamat, idProvinsi, idKota, idKecamatan
               )
        from Gudang 
        where idWorkspace = :idWorkspace AND isDeleted != true
        """)
    List<GudangDto> getGudangByIdWorkspace(@Param("idWorkspace") Long idWorkspace);

    Gudang findByIdWorkspaceAndIsDefault(Long idWorkspace, Boolean isDefault);

    @Transactional
    @Modifying
    @Query("update Gudang g set g.isDeleted = true where g.id in :idGudang")
    int deleteGudang(@Param("idGudang") Long idGudang);
}