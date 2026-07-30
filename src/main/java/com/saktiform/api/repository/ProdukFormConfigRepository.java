package com.saktiform.api.repository;

import com.saktiform.api.entity.ProdukFormConfig;
import com.saktiform.api.model.product.formconfig.FieldCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProdukFormConfigRepository extends JpaRepository<ProdukFormConfig, Long> {

    /**
     * @deprecated <b>Jangan dipanggil dari jalur simpan produk.</b> Pola
     *             delete-and-reinsert pada {@code saveProduct()} menyebabkan seluruh
     *             konfigurasi — termasuk System Field — lenyap ketika produk disimpan
     *             tanpa memuat {@code formConfig}. Penghapusan field hanya boleh melalui
     *             {@code PUT /produk/{id}/form-config} yang memiliki guard lengkap.
     *             Dipertahankan untuk pembersihan administratif saja.
     */
    @Deprecated
    void deleteProdukFormConfigByIdProduk(UUID idProduk);

    List<ProdukFormConfig> getProdukFormConfigsByIdProduk(UUID idProduk);

    /** Seluruh field satu produk, terurut. {@code IdAsc} sebagai tie-breaker deterministik. */
    List<ProdukFormConfig> findByIdProdukOrderBySortOrderAscIdAsc(UUID idProduk);

    /** Field aktif saja — dipakai halaman checkout publik. */
    List<ProdukFormConfig> findByIdProdukAndIsActiveTrueOrderBySortOrderAscIdAsc(UUID idProduk);

    Optional<ProdukFormConfig> findByIdProdukAndFieldKey(UUID idProduk, String fieldKey);

    long countByIdProdukAndFieldCategoryAndIsActiveTrue(UUID idProduk, FieldCategory category);

    void deleteByIdProdukAndFieldKeyIn(UUID idProduk, Collection<String> fieldKeys);
}
