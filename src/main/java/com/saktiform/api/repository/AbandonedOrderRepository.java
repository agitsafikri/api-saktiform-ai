package com.saktiform.api.repository;

import com.saktiform.api.entity.AbandonedOrder;
import com.saktiform.api.model.Order.AbandonedListDto;
import com.saktiform.api.model.Order.AbandonedOrderDetailDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface AbandonedOrderRepository extends JpaRepository<AbandonedOrder, UUID> {
    @Query("SELECT a.id FROM AbandonedOrder a ")
    Iterable<UUID> findAllUsedIds();

    @Query("""
    Select  new com.saktiform.api.model.Order.AbandonedListDto(
        abd.id, abd.namaPenerima, abd.nomorWhatsapp, prd.namaProduk, abd.alamat, prv.provinceName, kt.cityName, kc.districtName)
         from AbandonedOrder as abd
        join abd.produk as prd
        left join abd.provinsi as prv
        left join abd.kota as kt
        left join abd.kecamatan as kc
        
            where prd.idWorkspace = :idWorkspace
    """)
    Page<AbandonedListDto> getListAbandonedOrders(@Param("idWorkspace") Long idWorkspace, Pageable pageable);

    @Query("""
    Select  new com.saktiform.api.model.Order.AbandonedOrderDetailDto(
        abd.id, abd.namaPenerima, abd.nomorWhatsapp, prd.id, prd.namaProduk, abd.alamat, prv.id, prv.provinceName, kt.id, kt.cityName, kc.id, kc.districtName, abd.pembayaran)
        from AbandonedOrder as abd
        join abd.produk as prd
        left join abd.provinsi as prv
        left join abd.kota as kt
        left join abd.kecamatan as kc
        
            where abd.id = :idAbandonedOrder
    """)
    AbandonedOrderDetailDto getDetailAbandonedOrders(@Param("idAbandonedOrder") UUID idAbandonedOrder);

    @Transactional
    @Modifying
    @Query("DELETE FROM  AbandonedOrder a where  a.namaPenerima ILIKE '%:namaPenerima%' OR a.nomorWhatsapp ILIKE '%:nomorWhatsapp%'")
    void deletedAbandonedOrder(@Param("namaPenerima")String namaPenerima, @Param("nomorWhatsapp")String nomorWhatsapp);
}