package com.saktiform.api.repository;

import com.saktiform.api.entity.OrderCustomField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderCustomFieldRepository extends JpaRepository<OrderCustomField, Long> {

    List<OrderCustomField> findByIdOrderOrderBySortOrderAscIdAsc(UUID idOrder);

    /** Batch fetch untuk daftar order — mencegah kueri N+1. */
    List<OrderCustomField> findByIdOrderInOrderBySortOrderAscIdAsc(Collection<UUID> idOrders);

    long countByIdProdukAndFieldKey(UUID idProduk, String fieldKey);

    /**
     * usageCount seluruh field satu produk dalam <b>satu</b> kueri agregat.
     * Dilayani index {@code idx_ocf_produk_field}, tanpa join ke tabel {@code order}.
     */
    @Query("""
            select f.fieldKey as fieldKey, count(f) as usageCount
            from OrderCustomField f
            where f.idProduk = :idProduk
            group by f.fieldKey
            """)
    List<FieldUsageProjection> countUsageByProduk(@Param("idProduk") UUID idProduk);

    /** usageCount terbatas pada kandidat hapus — dipakai saat menyimpan konfigurasi. */
    @Query("""
            select f.fieldKey as fieldKey, count(f) as usageCount
            from OrderCustomField f
            where f.idProduk = :idProduk and f.fieldKey in :fieldKeys
            group by f.fieldKey
            """)
    List<FieldUsageProjection> countUsageByProdukAndFieldKeys(@Param("idProduk") UUID idProduk,
                                                              @Param("fieldKeys") Collection<String> fieldKeys);

    interface FieldUsageProjection {
        String getFieldKey();

        Long getUsageCount();
    }
}
