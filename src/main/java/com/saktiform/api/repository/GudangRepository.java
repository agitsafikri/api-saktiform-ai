package com.saktiform.api.repository;

import com.saktiform.api.entity.Gudang;
import com.saktiform.api.model.gudang.GudangDetailResponse;
import com.saktiform.api.model.gudang.GudangDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                gd.id, gd.namaGudang, gd.alamat, prov.provinceName, ct.cityName, dt.districtName
               )
        from Gudang gd
                JOIN Province prov on gd.idProvinsi = prov.id
                JOIN City ct on gd.idKota = ct.id
                JOIN District dt on gd.idKecamatan = dt.id
        where gd.idWorkspace = :idWorkspace AND gd.isDeleted != true
        """)
    Page<GudangDto> getGudangByIdWorkspace(@Param("idWorkspace") Long idWorkspace, Pageable pageable);

    Gudang findByIdWorkspaceAndIsDefault(Long idWorkspace, Boolean isDefault);

    @Transactional
    @Modifying
    @Query("update Gudang g set g.isDeleted = true where g.id in :idGudang")
    int deleteGudang(@Param("idGudang") Long idGudang);

    @Query(""" 
        SELECT new com.saktiform.api.model.gudang.GudangDetailResponse(
                gd.id,
                gd.namaGudang,
                gd.alamat,
                prov.id,
                prov.provinceName,
                ct.id,
                ct.cityName,
                dt.id,
                dt.districtName,
                gd.idWorkspace
            ) FROM Gudang gd 
                JOIN Province prov on gd.idProvinsi = prov.id
                JOIN City ct on gd.idKota = ct.id
                JOIN District dt on gd.idKecamatan = dt.id
                    WHERE gd.idWorkspace = :idGudang
    """)
    GudangDetailResponse getGudangDetailById(@Param("idGudang") Long idGudang);
}