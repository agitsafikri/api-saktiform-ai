package com.saktiform.api.repository;

import com.saktiform.api.entity.Produk;
import com.saktiform.api.model.product.ProdukListDropdown;
import com.saktiform.api.model.product.ProdukListDto;
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

public interface ProdukRepository extends JpaRepository<Produk, UUID> {

    @Query("""
        SELECT new com.saktiform.api.model.product.ProdukListDto(
                    p.id,
                    p.namaProduk,
                    (SELECT MIN(ap.harga) FROM AtributProduk ap WHERE ap.produk = p),
                    p.orderCount,
                    p.soldCount
                )
        FROM Produk as p
        WHERE p.idWorkspace = :idWorkspace AND p.isDeleted != TRUE
        """)
    Page<ProdukListDto> findAllProdukListDto(@Param("idWorkspace") Long idWorkspace,
                                             Pageable pageable);

    @Query("""
        SELECT new com.saktiform.api.model.product.ProdukListDropdown(
                    p.id,
                    p.namaProduk
                )
        FROM Produk as p
        WHERE p.idWorkspace = :idWorkspace AND p.isDeleted != TRUE
        ORDER BY p.namaProduk
        """)
    List<ProdukListDropdown> findAllProdukListDropdown(@Param("idWorkspace") Long idWorkspace);

    @Query("""
        SELECT new com.saktiform.api.model.product.ProdukListDto(
                    p.id,
                    p.namaProduk,
                    (SELECT MIN(ap.harga) FROM AtributProduk ap WHERE ap.produk = p),
                    p.orderCount,
                    p.soldCount
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