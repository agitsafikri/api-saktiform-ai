package com.saktiform.api.repository;

import com.saktiform.api.entity.Order;
import com.saktiform.api.model.Order.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query(
            value = """
        SELECT 
            ord.id AS id,
            ord.nama_penerima AS namaCustomer,
            ord.order_code as orderCode,
            ord.nomor_whatsapp AS nomorWhatsapp,
            prod.nama_produk AS namaProduk,
            prov.province_name AS provinsi,
            ord.status AS status,
            ord.notes AS notes,
            ord.created_at AS tanggalOrderRaw,
            ord.paid_at AS paidAtRaw,
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
         AND (
                (:search IS NULL OR ord.order_code ILIKE  CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR prod.nama_produk ILIKE CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR prov.province_name ILIKE CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR ord.nama_penerima ILIKE CONCAT('%', :search, '%'))
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
          AND
                (
                (:search IS NULL OR ord.order_code ILIKE  CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR prod.nama_produk ILIKE CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR prov.province_name ILIKE CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR ord.nama_penerima ILIKE CONCAT('%', :search, '%'))
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
                                    @Param("search") String search,
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
            ord.created_at AS tanggalOrderRaw,
            ord.paid_at AS paidAtRaw,
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
                AND (
                (:search IS NULL OR ord.order_code ILIKE  CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR prod.nama_produk ILIKE CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR prov.province_name ILIKE CONCAT('%', :search, '%'))
                OR
                (:search IS NULL OR ord.nama_penerima ILIKE CONCAT('%', :search, '%'))
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
                                         @Param("createdAtStart") LocalDateTime createdAtStart,
                                         @Param("createdAtEnd")LocalDateTime  createdAtEnd,
                                         @Param("paidAtStart") LocalDateTime paidAtStart,
                                         @Param("paidAtEnd")LocalDateTime  paidAtEnd,
                                         @Param("sentinel")LocalDateTime  sentinel,
                                         @Param("tomorow")LocalDateTime  tomorow,
                                         @Param("search") String search);

    @Modifying
    @Query("UPDATE Order o SET o.statusEkspor = true WHERE o.id IN :ids")
    int markAsExported(@Param("ids") List<UUID> ids);


    @Query("""
        SELECT new com.saktiform.api.model.Order.ConversationOrderList(
                ord.id,
                ord.createdAt,
                ord.produk.namaProduk,
                ord.orderCode,
                ord.status
            )
        FROM Order ord where ord.idConversation = :idConversation
    """)
    List <ConversationOrderList> getConversationOrderList(@Param("idConversation") UUID idConversation);

    @Query(value = """
        SELECT 
            DATE(o.paid_at) as date,
            SUM(o.harga) as pendapatan
        FROM public.order o
        WHERE o.paid_at BETWEEN :startDate AND :endDate
        AND o.status = 'PAID' 
        GROUP BY DATE(o.paid_at)
        ORDER BY DATE(o.paid_at)
    """, nativeQuery = true)
    List<TotalPendapatanReportView> getDailyReportPendapatan(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate
    );

    @Query(value = """
    WITH days AS (
        SELECT generate_series(
            CAST(:startDate AS date),
            CAST(:endDate AS date),
            INTERVAL '1 day'
        )::date AS tanggal
    ),
    orders_filtered AS (
        SELECT o.*
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
    )
    
    SELECT
        d.tanggal as date,
        COUNT(of.id) FILTER (
            WHERE of.created_at >= d.tanggal
              AND of.created_at < d.tanggal + INTERVAL '1 day'
        ) AS jumlahOrder,
    
        COUNT(of.id) FILTER (
            WHERE of.status = 'PAID'
              AND of.paid_at >= d.tanggal
              AND of.paid_at < d.tanggal + INTERVAL '1 day'
        ) AS jumlahBayar
    
    FROM days d
    LEFT JOIN orders_filtered of
        ON (
            of.created_at >= d.tanggal
            AND of.created_at < d.tanggal + INTERVAL '1 day'
        )
        OR (
            of.status = 'PAID'
            AND of.paid_at >= d.tanggal
            AND of.paid_at < d.tanggal + INTERVAL '1 day'
        )
    
    GROUP BY d.tanggal
    ORDER BY d.tanggal;
    
    """, nativeQuery = true)
    List<TotalOrderReportView> getDailyReportTotalOrder(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace")Long idWorkspace
    );

    @Query(value = """

            WITH weeks AS (
        SELECT generate_series(
            DATE_TRUNC('week', CAST(:startDate AS timestamp)),
            DATE_TRUNC('week', CAST(:endDate AS timestamp)),
            INTERVAL '1 week'
        )::date AS week_start
    ),
    orders_filtered AS (
        SELECT o.*
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
    )
    
    SELECT
        -- Format: 10 Feb - 16 Feb 2026
        TO_CHAR(w.week_start, 'DD Mon')\s
        || ' - ' ||
        TO_CHAR(w.week_start + INTERVAL '6 day', 'DD Mon YYYY')
        AS date,
    
        COUNT(of.id) FILTER (
            WHERE of.created_at >= w.week_start
              AND of.created_at < w.week_start + INTERVAL '1 week'
        ) AS jumlahOrder,
    
        COUNT(of.id) FILTER (
            WHERE of.status = 'PAID'
              AND of.paid_at >= w.week_start
              AND of.paid_at < w.week_start + INTERVAL '1 week'
        ) AS jumlahBayar
    
    FROM weeks w
    LEFT JOIN orders_filtered of
        ON (
            of.created_at >= w.week_start
            AND of.created_at < w.week_start + INTERVAL '1 week'
        )
        OR (
            of.status = 'PAID'
            AND of.paid_at >= w.week_start
            AND of.paid_at < w.week_start + INTERVAL '1 week'
        )
    
    GROUP BY w.week_start
    ORDER BY w.week_start;
    
        """, nativeQuery = true)
    List<TotalOrderReportView> getWeeklyReportTotalOrder(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace")Long idWorkspace
    );

    @Query(value = """
        SELECT 
            DATE_TRUNC('week', o.paid_at)::date as date,
            SUM(o.harga) as pendapatan,
            COUNT(o.id) as jumlahOrder
        FROM public.order o
        WHERE o.paid_at BETWEEN :startDate AND :endDate
        AND o.status = 'PAID'
        GROUP BY DATE_TRUNC('week', o.paid_at)
        ORDER BY DATE_TRUNC('week', o.paid_at)
    """, nativeQuery = true)
    List<TotalPendapatanReportView> getWeeklyReportPendapatan(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate
    );

    @Query(value = """
WITH months AS (
    SELECT generate_series(
        DATE_TRUNC('month', CAST(:startDate AS timestamp)),
        DATE_TRUNC('month', CAST(:endDate AS timestamp)),
        INTERVAL '1 month'
    )::date AS month_date
),
orders_filtered AS (
    SELECT o.*
    FROM public."order" o
    JOIN public.produk p ON p.id = o.id_produk
    WHERE p.id_workspace = :idWorkspace
)

SELECT
    TO_CHAR(m.month_date, 'YYYY-Mon') AS date,
    COUNT(of.id) FILTER (
        WHERE of.created_at >= m.month_date
          AND of.created_at < m.month_date + INTERVAL '1 month'
    ) AS jumlahOrder,

    COUNT(of.id) FILTER (
        WHERE of.status = 'PAID'
          AND of.paid_at >= m.month_date
          AND of.paid_at < m.month_date + INTERVAL '1 month'
    ) AS jumlahBayar

FROM months m
LEFT JOIN orders_filtered of
    ON (
        of.created_at >= m.month_date
        AND of.created_at < m.month_date + INTERVAL '1 month'
    )
    OR (
        of.status = 'PAID'
        AND of.paid_at >= m.month_date
        AND of.paid_at < m.month_date + INTERVAL '1 month'
    )

GROUP BY m.month_date
ORDER BY m.month_date;

""", nativeQuery = true)
    List<TotalOrderReportView> getMonthlyReportTotalOrder(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace")Long idWorkspace
    );

    @Query(value = """
        SELECT 
            DATE_TRUNC('month', o.paid_at)::date as date,
            SUM(o.harga) as pendapatan
        FROM public.order o
        WHERE o.paid_at BETWEEN :startDate AND :endDate
        AND o.status = 'PAID'
        GROUP BY DATE_TRUNC('month', o.paid_at)
        ORDER BY DATE_TRUNC('month', o.paid_at)
        """, nativeQuery = true)
    List<TotalPendapatanReportView> getMonthlyReportPendapatan(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate
    );

    @Query("""
    SELECT o from Order o
        join o.produk p
        where o.namaPenerima = :namaPenerima
            AND p.idWorkspace = :idWorkspace
            AND o.createdAt BETWEEN :startDate AND :endDate
            AND p.namaProduk ILIKE :namaProduk
        ORDER BY o.createdAt DESC 
    """)
    List<Order> searchOrderForConfirmation(@Param("namaPenerima") String namaPenerima, @Param("idWorkspace") Long idWorkspace, @Param("namaProduk") String namaProduk, @Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("""
        SELECT c.idWorkspace FROM Conversation conv
        JOIN conv.contact c
            WHERE conv.id = :idConversation
    """)
    Long getIdWorkspaceByConversationId(@Param("idConversation") UUID idConversation);

    @Query("SELECT o.id FROM Order o where o.idConversation = :idConversation")
    List<UUID> getOrderIdsByIdConversation(@Param("idConversation") UUID idConversation);

}