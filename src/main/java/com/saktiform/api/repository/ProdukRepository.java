package com.saktiform.api.repository;

import com.saktiform.api.entity.Produk;
import com.saktiform.api.model.product.ProdukListDropdown;
import com.saktiform.api.model.product.ProdukListDto;
import com.saktiform.api.model.product.ProdukOrderStatistikView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProdukRepository extends JpaRepository<Produk, UUID> {

    /**
     * Menambah / mengurangi counter produk secara atomik di database (bebas lost update
     * saat ada request bersamaan, dan aman untuk kolom yang masih NULL).
     *
     * <p>Bulk update ini melewati persistence context: entity Produk yang sedang
     * di-load tidak ikut ter-update, jadi jangan mengubah lalu menyimpan counter
     * lewat entity pada transaksi yang sama.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE Produk p
        SET p.orderCount = COALESCE(p.orderCount, 0) + :orderDelta,
            p.soldCount  = COALESCE(p.soldCount, 0) + :soldDelta
        WHERE p.id = :idProduk
        """)
    int adjustCounters(@Param("idProduk") UUID idProduk,
                       @Param("orderDelta") long orderDelta,
                       @Param("soldDelta") long soldDelta);

    @Query("""
        SELECT new com.saktiform.api.model.product.ProdukListDto(
                    p.id,
                    p.namaProduk,
                    (SELECT MIN(ap.harga) FROM AtributProduk ap WHERE ap.produk = p),
                    p.orderCount,
                    p.soldCount,
                    p.urlCheckout
                )
        FROM Produk as p
        WHERE p.idWorkspace = :idWorkspace AND p.isDeleted != TRUE
        """)
    Page<ProdukListDto> findAllProdukListDto(@Param("idWorkspace") Long idWorkspace,
                                             Pageable pageable);

    /**
     * Statistik order untuk sekumpulan produk (satu halaman list produk), dibatasi tanggal
     * order masuk ({@code order.created_at}). {@code paid_at} sengaja tidak dipakai: "dibayar"
     * berarti order yang masuk dalam rentang dan saat ini berstatus PAID.
     *
     * <p>Definisi sama dengan counter produk ({@code OrderService.applyProdukCounterChange})
     * dan dashboard workspace: DELETE tidak dihitung, status NULL tetap dihitung sebagai order.
     * Produk tanpa order dalam rentang tidak muncul di hasil (diperlakukan 0 oleh pemanggil).
     * Ditopang index {@code idx_order_produk_created_at (id_produk, created_at DESC)}.
     */
    @Query(value = """
        SELECT
            ord.id_produk AS idProduk,
            COUNT(*) FILTER (WHERE ord.status IS DISTINCT FROM 'DELETE') AS totalOrder,
            COUNT(*) FILTER (WHERE ord.status = 'PAID') AS totalDibayar
        FROM public."order" ord
        WHERE ord.id_produk IN (:idProduk)
          AND (CAST(:createdAtStart AS timestamp) IS NULL
               OR ord.created_at >= CAST(:createdAtStart AS timestamp))
          AND (CAST(:createdAtEnd AS timestamp) IS NULL
               OR ord.created_at <= CAST(:createdAtEnd AS timestamp))
        GROUP BY ord.id_produk
        """,
            nativeQuery = true)
    List<ProdukOrderStatistikView> getOrderStatistikByProduk(@Param("idProduk") Collection<UUID> idProduk,
                                                             @Param("createdAtStart") LocalDateTime createdAtStart,
                                                             @Param("createdAtEnd") LocalDateTime createdAtEnd);

    /**
     * Filter dropdown produk: workspace, belum dihapus, dan (opsional) nama produk
     * mengandung {@code search} tanpa peka huruf. {@code search = ''} berarti tanpa filter nama.
     */
    String PRODUK_DROPDOWN_FILTER = """
        FROM Produk as p
        WHERE p.idWorkspace = :idWorkspace AND p.isDeleted != TRUE
          AND (:search = '' OR lower(p.namaProduk) LIKE concat('%', lower(:search), '%'))
        """;

    // Urutan diambil dari Sort (namaProduk, lalu id agar halaman stabil saat nama sama).
    @Query("""
        SELECT new com.saktiform.api.model.product.ProdukListDropdown(
                    p.id,
                    p.namaProduk
                )
        """ + PRODUK_DROPDOWN_FILTER)
    List<ProdukListDropdown> findAllProdukListDropdown(@Param("idWorkspace") Long idWorkspace,
                                                       @Param("search") String search,
                                                       Sort sort);

    @Query(value = """
        SELECT new com.saktiform.api.model.product.ProdukListDropdown(
                    p.id,
                    p.namaProduk
                )
        """ + PRODUK_DROPDOWN_FILTER,
            countQuery = "SELECT COUNT(p) " + PRODUK_DROPDOWN_FILTER)
    Page<ProdukListDropdown> findPageProdukListDropdown(@Param("idWorkspace") Long idWorkspace,
                                                        @Param("search") String search,
                                                        Pageable pageable);

    @Query("""
        SELECT new com.saktiform.api.model.product.ProdukListDto(
                    p.id,
                    p.namaProduk,
                    (SELECT MIN(ap.harga) FROM AtributProduk ap WHERE ap.produk = p),
                    p.orderCount,
                    p.soldCount,
                    p.urlCheckout
                )
        FROM Produk as p
        WHERE p.idWorkspace = :idWorkspace 
                AND p.isDeleted != TRUE 
                AND lower(p.namaProduk)  LIKE %:search%
        """)
    Page<ProdukListDto> findAllProdukListDtoSearch(@Param("idWorkspace") Long idWorkspace,
                                             @Param("search") String search,
                                             Pageable pageable);

    @Query("""
        SELECT p
        FROM Produk as p
        WHERE p.isDeleted != TRUE
                AND lower(p.urlCheckout)  = lower(:urlCheckout)
        ORDER BY p.createdAt DESC
    """)
    List<Produk> findByUrlCheckout(String urlCheckout);

    @Query("""
        SELECT p
        FROM Produk as p
        WHERE p.isDeleted != TRUE
                AND lower(p.namaProduk)  = lower(:namaProduk)
                AND p.idWorkspace = :idWorkspace
        ORDER BY p.createdAt DESC
    """)
    List<Produk> findByNamaProduk(@Param("namaProduk") String namaProduk, @Param("idWorkspace") Long idWorkspace);

    @Query(
    """
        select count (p.id) from Produk p where p.namaProduk like %:productName% and p.isDeleted != TRUE AND p.idWorkspace = :idWorkspace
    """
    )
    Integer countIdenticProductName(@Param("productName") String produkName, @Param("idWorkspace") Long idWorkspace);

    @Query(
            """
                select count (p.id) from Produk p where lower(p.urlCheckout)  like concat('%',lower(:url),'%')
            """
    )
    Integer countIdenticProductUrl(@Param("url") String url);

    @Transactional
    @Modifying
    @Query("update Produk p set p.isDeleted = ?1 where p.id in ?2")
    int updateIsDeletedByIdIn(Boolean isDeleted, Collection<UUID> ids);



    Produk findByUrlCheckoutAndIsDeleted(String urlCheckout, Boolean isDeleted);

    Produk findByNamaProdukAndIsDeletedAndIdWorkspace(String namaProduk, Boolean isDeleted, Long idWorkspace);
}