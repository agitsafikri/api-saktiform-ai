package com.saktiform.api.repository;

import com.saktiform.api.entity.Order;
import com.saktiform.api.model.Order.ConversationOrderList;
import com.saktiform.api.model.Order.ExportOrderListDto;
import com.saktiform.api.model.Order.OrderListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query(
            value = """
        SELECT 
            ord.id AS id,
            ord.nama_penerima AS namaCustomer,
            ord.nomor_whatsapp AS nomorWhatsapp,
            prod.nama_produk AS namaProduk,
            prov.province_name AS provinsi,
            ord.status AS status,
            ord.notes AS notes,
            TO_CHAR(ord.created_at, 'YYYY-MM-DD HH24:MI:SS') AS tanggalOrder,
            TO_CHAR(ord.paid_at, 'YYYY-MM-DD HH24:MI:SS') AS paidAt,
            ord.pembayaran AS jenisPembayaran,
            ord.status_ekspor AS statusEkspor
        FROM public.order ord
        JOIN produk prod ON ord.id_produk = prod.id
        JOIN province prov ON prov.province_id = ord.id_provinsi
        WHERE prod.id_workspace = :idWorkspace
          AND (:idProvinsi IS NULL OR ord.id_provinsi = :idProvinsi)
          AND (:idKota IS NULL OR ord.id_kota = :idKota)
          AND (:idKecamatan IS NULL OR ord.id_kecamatan = :idKecamatan)
          AND (:status IS NULL OR ord.status = :status)
          AND (:jenisPembayaran IS NULL OR ord.pembayaran = :jenisPembayaran)
          AND (:statusEkspor IS NULL OR ord.status_ekspor = :statusEkspor)
          AND (
                CAST(COALESCE(ord.created_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:createdAtStart, :sentinel) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.created_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:createdAtEnd, :tomorow) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.paid_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:paidAtStart, :sentinel) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.paid_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:paidAtEnd, :tomorow) AS timestamp)
          )
        """,
            countQuery = """
        SELECT COUNT(*)
        FROM public.order ord
        JOIN produk prod ON ord.id_produk = prod.id
        JOIN province prov ON prov.province_id = ord.id_provinsi
        WHERE prod.id_workspace = :idWorkspace
          AND (:idProvinsi IS NULL OR ord.id_provinsi = :idProvinsi)
          AND (:idKota IS NULL OR ord.id_kota = :idKota)
          AND (:idKecamatan IS NULL OR ord.id_kecamatan = :idKecamatan)
          AND (:status IS NULL OR ord.status = :status)
          AND (:jenisPembayaran IS NULL OR ord.pembayaran = :jenisPembayaran)
          AND (:statusEkspor IS NULL OR ord.status_ekspor = :statusEkspor)
          AND (
                CAST(COALESCE(ord.created_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:createdAtStart, :sentinel) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.created_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:createdAtEnd, :tomorow) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.paid_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:paidAtStart, :sentinel) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.paid_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:paidAtEnd, :tomorow) AS timestamp)
          )
        """,
            nativeQuery = true
    )
    Page<OrderListDto> getOrderList(@Param("idWorkspace") Long idWorkspace,
                                    @Param("idProvinsi") Integer idProvinsi,
                                    @Param("idKota") Integer idKota,
                                    @Param("idKecamatan")Integer idKecamatan,
                                    @Param("status")String status,
                                    @Param("jenisPembayaran")String jenisPembayaran,
                                    @Param("statusEkspor")Boolean statusEkspor,
                                    @Param("createdAtStart")LocalDateTime  createdAtStart,
                                    @Param("createdAtEnd")LocalDateTime  createdAtEnd,
                                    @Param("paidAtStart") LocalDateTime paidAtStart,
                                    @Param("paidAtEnd")LocalDateTime  paidAtEnd,
                                    @Param("sentinel")LocalDateTime  sentinel,
                                    @Param("tomorow")LocalDateTime  tomorow,
                                    Pageable pageable);

    Order findOrderById(UUID id);

    @Query(
            value = """
        SELECT 
            ord.id as id,
            ord.order_code AS orderCode,
            ord.nama_penerima AS namaCustomer,
            ord.nomor_whatsapp AS nomorWhatsapp,
            prod.nama_produk AS namaProduk,
            ord.ongkos_kirim as ongkir,
            ord.harga as harga,
            ord.diskon as diskon,
            ord.deskripsi_produk as variasi,
            ord.alamat as alamat,
            prov.province_name AS provinsi,
            ct.city_name as kota,
            dt.district_name as kecamatan,
            ord.status AS status,
            ord.notes AS notes,
            TO_CHAR(ord.created_at, 'YYYY-MM-DD HH24:MI:SS') AS tanggalOrder,
            TO_CHAR(ord.paid_at, 'YYYY-MM-DD HH24:MI:SS') AS paidAt,
            ord.pembayaran AS jenisPembayaran,
            acc.username AS handleBy,
            ord.deskripsi_produk as variation,
            ord.berat as berat
        FROM public.order ord
        JOIN produk prod ON ord.id_produk = prod.id
        JOIN province prov ON prov.province_id = ord.id_provinsi
        JOIN city ct ON ct.city_id = ord.id_kota
        JOIN district dt ON dt.district_id = ord.id_kecamatan
        LEFT JOIN account acc ON acc.id = ord.last_handle_by
        WHERE prod.id_workspace = :idWorkspace
          AND (:idProvinsi IS NULL OR ord.id_provinsi = :idProvinsi)
          AND (:idKota IS NULL OR ord.id_kota = :idKota)
          AND (:idKecamatan IS NULL OR ord.id_kecamatan = :idKecamatan)
          AND (:status IS NULL OR ord.status = :status)
          AND (:jenisPembayaran IS NULL OR ord.pembayaran = :jenisPembayaran)
          AND (:statusEkspor IS NULL OR ord.status_ekspor = :statusEkspor)
          AND (
                CAST(COALESCE(ord.created_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:createdAtStart, :sentinel) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.created_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:createdAtEnd, :tomorow) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.paid_at, :sentinel) AS timestamp) 
                >= CAST(COALESCE(:paidAtStart, :sentinel) AS timestamp)
          )
          AND (
                CAST(COALESCE(ord.paid_at, :tomorow) AS timestamp) 
                <= CAST(COALESCE(:paidAtEnd, :tomorow) AS timestamp)
          )
        """,
            nativeQuery = true
    )
    List<ExportOrderListDto> exportOrder(@Param("idWorkspace") Long idWorkspace,
                                         @Param("idProvinsi") Integer idProvinsi,
                                         @Param("idKota") Integer idKota,
                                         @Param("idKecamatan")Integer idKecamatan,
                                         @Param("status")String status,
                                         @Param("jenisPembayaran")String jenisPembayaran,
                                         @Param("statusEkspor")Boolean statusEkspor,
                                         @Param("createdAtStart")LocalDateTime  createdAtStart,
                                         @Param("createdAtEnd")LocalDateTime  createdAtEnd,
                                         @Param("paidAtStart") LocalDateTime paidAtStart,
                                         @Param("paidAtEnd")LocalDateTime  paidAtEnd,
                                         @Param("sentinel")LocalDateTime  sentinel,
                                         @Param("tomorow")LocalDateTime  tomorow);

    @Modifying
    @Query("UPDATE Order o SET o.statusEkspor = true WHERE o.id IN :ids")
    int markAsExported(@Param("ids") List<UUID> ids);


    @Query("""
        SELECT new com.saktiform.api.model.Order.ConversationOrderList(
                ord.id,
                ord.createdAt,
                ord.produk.namaProduk,
                ord.deskripsiProduk,
                ord.status
            )
        FROM Order ord where ord.idConversation = :idConversation
    """)
    List <ConversationOrderList> getConversationOrderList(@Param("idConversation") UUID idConversation);
}