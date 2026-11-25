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
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Filter bersama untuk getOrderList (data + count) dan exportOrder, supaya ketiganya
     * tidak bisa lagi berbeda isi. Mengharapkan alias {@code ord} (order) dan {@code prod} (produk).
     *
     * Catatan performa:
     * - Filter tanggal membandingkan kolom secara langsung (tanpa COALESCE/CAST pada kolom)
     *   sehingga index pada created_at / paid_at bisa dipakai.
     * - Filter isDouble mencari nomor duplikat sekali (GROUP BY ... HAVING) alih-alih
     *   EXISTS berkorelasi yang dieksekusi per baris.
     */
    String ORDER_LIST_FILTER = """
            WHERE prod.id_workspace = :idWorkspace

              -- Owner dapat melihat order DELETE.
              -- Non-owner tidak dapat melihat order DELETE.
              AND (:isOwner = TRUE OR ord.status <> 'DELETE')

              -- Filter multi-nilai: string dipisah koma, '' = tanpa filter (OrderListFilter).
              -- Isinya sudah divalidasi OrderListFilterParser, sehingga CAST int[] aman.
              AND (:idProvinsi = '' OR ord.id_provinsi = ANY(CAST(string_to_array(:idProvinsi, ',') AS int[])))
              AND (:idKota = '' OR ord.id_kota = ANY(CAST(string_to_array(:idKota, ',') AS int[])))
              AND (:idKecamatan = '' OR ord.id_kecamatan = ANY(CAST(string_to_array(:idKecamatan, ',') AS int[])))
              AND (:status = '' OR ord.status = ANY(string_to_array(:status, ',')))
              AND (:jenisPembayaran = '' OR ord.pembayaran = ANY(string_to_array(:jenisPembayaran, ',')))
              -- Isi sudah divalidasi berformat UUID, sehingga CAST uuid[] aman.
              AND (:idProduk = '' OR ord.id_produk = ANY(CAST(string_to_array(:idProduk, ',') AS uuid[])))
              -- status_ekspor tidak punya default di DB: NULL berarti belum pernah diekspor,
              -- sehingga statusEkspor=false harus mencakup FALSE maupun NULL.
              AND (:statusEkspor IS NULL OR COALESCE(ord.status_ekspor, FALSE) = :statusEkspor)

              AND (CAST(:createdAtStart AS timestamp) IS NULL
                   OR ord.created_at >= CAST(:createdAtStart AS timestamp))
              AND (CAST(:createdAtEnd AS timestamp) IS NULL
                   OR ord.created_at <= CAST(:createdAtEnd AS timestamp))
              AND (CAST(:paidAtStart AS timestamp) IS NULL
                   OR ord.paid_at >= CAST(:paidAtStart AS timestamp))
              AND (CAST(:paidAtEnd AS timestamp) IS NULL
                   OR ord.paid_at <= CAST(:paidAtEnd AS timestamp))

              AND (:orderCode = '' OR ord.order_code = ANY(string_to_array(:orderCode, ',')))
              AND (:namaProduk = '' OR prod.nama_produk ILIKE CONCAT('%', :namaProduk, '%'))
              AND (:namaPenerima = '' OR ord.nama_penerima ILIKE CONCAT('%', :namaPenerima, '%'))
              AND (:nomorWhatsapp = '' OR ord.nomor_whatsapp ILIKE CONCAT('%', :nomorWhatsapp, '%'))

              AND (
                  :isDouble = FALSE
                  OR ord.nomor_whatsapp IN (
                      SELECT o2.nomor_whatsapp
                      FROM public."order" o2
                      JOIN produk p2 ON p2.id = o2.id_produk
                      WHERE p2.id_workspace = :idWorkspace
                        AND TRIM(o2.nomor_whatsapp) <> ''
                      GROUP BY o2.nomor_whatsapp
                      HAVING COUNT(*) > 1
                  )
              )
            """;

    // Tanpa ORDER BY: urutan diambil dari Sort di Pageable (lihat OrderService.getOrderList).
    // ORDER BY bawaan di sini akan didahulukan Spring Data dan mengalahkan urutan isDouble.
    @Query(
            value = """
            SELECT
                ord.id AS id,
                ord.nama_penerima AS namaCustomer,
                ord.order_code AS orderCode,
                ord.nomor_whatsapp AS nomorWhatsapp,
                prod.nama_produk AS namaProduk,
                prov.province_name AS provinsi,
                ord.status AS status,
                ord.notes AS notes,
                ord.created_at AS tanggalOrderRaw,
                ord.paid_at AS paidAtRaw,
                ord.pembayaran AS jenisPembayaran,
                -- NULL (belum pernah diekspor, kolom tanpa default) dikirim sebagai false.
                COALESCE(ord.status_ekspor, FALSE) AS statusEkspor
            FROM public."order" ord
            JOIN produk prod ON ord.id_produk = prod.id
            JOIN province prov ON prov.province_id = ord.id_provinsi
            """ + ORDER_LIST_FILTER,

            // JOIN province dipertahankan agar total sama dengan baris yang benar-benar tampil.
            countQuery = """
            SELECT COUNT(*)
            FROM public."order" ord
            JOIN produk prod ON ord.id_produk = prod.id
            JOIN province prov ON prov.province_id = ord.id_provinsi
            """ + ORDER_LIST_FILTER,

            nativeQuery = true
    )
    Page<OrderListDto> getOrderList(
            @Param("idWorkspace") Long idWorkspace,
            @Param("idProvinsi") String idProvinsi,
            @Param("idKota") String idKota,
            @Param("idKecamatan") String idKecamatan,
            @Param("status") String status,
            @Param("jenisPembayaran") String jenisPembayaran,
            @Param("idProduk") String idProduk,
            @Param("statusEkspor") Boolean statusEkspor,
            @Param("createdAtStart") LocalDateTime createdAtStart,
            @Param("createdAtEnd") LocalDateTime createdAtEnd,
            @Param("paidAtStart") LocalDateTime paidAtStart,
            @Param("paidAtEnd") LocalDateTime paidAtEnd,
            @Param("orderCode") String orderCode,
            @Param("namaProduk") String namaProduk,
            @Param("namaPenerima") String namaPenerima,
            @Param("nomorWhatsapp") String nomorWhatsapp,
            @Param("isDouble") boolean isDouble,
            @Param("isOwner") boolean isOwner,
            Pageable pageable
    );

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
                ord.biaya_cod as biayaCod,
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
            FROM public."order" ord
            JOIN produk prod ON ord.id_produk = prod.id
            JOIN province prov ON prov.province_id = ord.id_provinsi
            JOIN city ct ON ct.city_id = ord.id_kota
            JOIN district dt ON dt.district_id = ord.id_kecamatan
            LEFT JOIN account acc ON acc.id = ord.last_handle_by
            """ + ORDER_LIST_FILTER + """
            ORDER BY
                CASE WHEN :isDouble = true THEN ord.nomor_whatsapp END ASC,
                ord.created_at DESC
            """,
            nativeQuery = true
    )
    List<ExportOrderListDto> exportOrder(@Param("idWorkspace") Long idWorkspace,
                                         @Param("idProvinsi") String idProvinsi,
                                         @Param("idKota") String idKota,
                                         @Param("idKecamatan") String idKecamatan,
                                         @Param("status")String status,
                                         @Param("jenisPembayaran")String jenisPembayaran,
                                         @Param("idProduk") String idProduk,
                                         @Param("statusEkspor")Boolean statusEkspor,
                                         @Param("createdAtStart") LocalDateTime createdAtStart,
                                         @Param("createdAtEnd")LocalDateTime  createdAtEnd,
                                         @Param("paidAtStart") LocalDateTime paidAtStart,
                                         @Param("paidAtEnd")LocalDateTime  paidAtEnd,
                                         @Param("orderCode") String orderCode,
                                         @Param("namaProduk") String namaProduk,
                                         @Param("namaPenerima") String namaPenerima,
                                         @Param("nomorWhatsapp") String nomorWhatsapp,
                                         @Param("isDouble") boolean isDouble,
                                         @Param("isOwner") boolean isOwner);

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
            AND (:isOwner = TRUE OR COALESCE(ord.status, '') <> 'DELETE')
    """)
    // Sama seperti getOrderList: hanya OWNER yang dapat melihat order DELETE.
    List <ConversationOrderList> getConversationOrderList(@Param("idConversation") UUID idConversation,
                                                          @Param("isOwner") boolean isOwner);

    @Query(value = """
        SELECT
            DATE(o.paid_at) as date,
            SUM(o.harga) as pendapatan
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status = 'PAID'
          AND o.paid_at BETWEEN :startDate AND :endDate
        GROUP BY DATE(o.paid_at)
        ORDER BY DATE(o.paid_at)
    """, nativeQuery = true)
    List<TotalPendapatanReportView> getDailyReportPendapatan(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace") Long idWorkspace
    );

    /*
     * Laporan total order (daily / weekly / monthly).
     *
     * Order diagregasi per bucket lebih dulu (sekali scan, dibatasi rentang tanggal sehingga
     * index created_at / paid_at bisa dipakai), baru di-LEFT JOIN ke deret tanggal dengan
     * kondisi equality. Versi lama memakai LEFT JOIN ... ON (...) OR (...) yang memaksa
     * nested loop "jumlah bucket x semua order workspace".
     */
    @Query(value = """
    WITH days AS (
        SELECT CAST(generate_series(
            CAST(:startDate AS date),
            CAST(:endDate AS date),
            INTERVAL '1 day'
        ) AS date) AS tanggal
    ),
    created AS (
        SELECT CAST(o.created_at AS date) AS tanggal, COUNT(*) AS jumlah
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status IS DISTINCT FROM 'DELETE'
          AND o.created_at >= CAST(:startDate AS date)
          AND o.created_at <  CAST(:endDate AS date) + 1
        GROUP BY 1
    ),
    paid AS (
        SELECT CAST(o.paid_at AS date) AS tanggal, COUNT(*) AS jumlah
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status = 'PAID'
          AND o.paid_at >= CAST(:startDate AS date)
          AND o.paid_at <  CAST(:endDate AS date) + 1
        GROUP BY 1
    )
    SELECT
        d.tanggal AS date,
        COALESCE(c.jumlah, 0) AS jumlahOrder,
        COALESCE(pd.jumlah, 0) AS jumlahBayar
    FROM days d
    LEFT JOIN created c ON c.tanggal = d.tanggal
    LEFT JOIN paid pd ON pd.tanggal = d.tanggal
    ORDER BY d.tanggal
    """, nativeQuery = true)
    List<TotalOrderReportView> getDailyReportTotalOrder(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace")Long idWorkspace
    );

    @Query(value = """
    WITH weeks AS (
        SELECT CAST(generate_series(
            DATE_TRUNC('week', CAST(:startDate AS timestamp)),
            DATE_TRUNC('week', CAST(:endDate AS timestamp)),
            INTERVAL '1 week'
        ) AS date) AS week_start
    ),
    created AS (
        SELECT CAST(DATE_TRUNC('week', o.created_at) AS date) AS week_start, COUNT(*) AS jumlah
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status IS DISTINCT FROM 'DELETE'
          AND o.created_at >= DATE_TRUNC('week', CAST(:startDate AS timestamp))
          AND o.created_at <  DATE_TRUNC('week', CAST(:endDate AS timestamp)) + INTERVAL '1 week'
        GROUP BY 1
    ),
    paid AS (
        SELECT CAST(DATE_TRUNC('week', o.paid_at) AS date) AS week_start, COUNT(*) AS jumlah
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status = 'PAID'
          AND o.paid_at >= DATE_TRUNC('week', CAST(:startDate AS timestamp))
          AND o.paid_at <  DATE_TRUNC('week', CAST(:endDate AS timestamp)) + INTERVAL '1 week'
        GROUP BY 1
    )
    SELECT
        -- Format: 10 Feb - 16 Feb 2026
        TO_CHAR(w.week_start, 'DD Mon')
        || ' - ' ||
        TO_CHAR(w.week_start + INTERVAL '6 day', 'DD Mon YYYY')
        AS date,
        COALESCE(c.jumlah, 0) AS jumlahOrder,
        COALESCE(pd.jumlah, 0) AS jumlahBayar
    FROM weeks w
    LEFT JOIN created c ON c.week_start = w.week_start
    LEFT JOIN paid pd ON pd.week_start = w.week_start
    ORDER BY w.week_start
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
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status = 'PAID'
          AND o.paid_at BETWEEN :startDate AND :endDate
        GROUP BY DATE_TRUNC('week', o.paid_at)
        ORDER BY DATE_TRUNC('week', o.paid_at)
    """, nativeQuery = true)
    List<TotalPendapatanReportView> getWeeklyReportPendapatan(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace") Long idWorkspace
    );

    @Query(value = """
    WITH months AS (
        SELECT CAST(generate_series(
            DATE_TRUNC('month', CAST(:startDate AS timestamp)),
            DATE_TRUNC('month', CAST(:endDate AS timestamp)),
            INTERVAL '1 month'
        ) AS date) AS month_date
    ),
    created AS (
        SELECT CAST(DATE_TRUNC('month', o.created_at) AS date) AS month_date, COUNT(*) AS jumlah
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status IS DISTINCT FROM 'DELETE'
          AND o.created_at >= DATE_TRUNC('month', CAST(:startDate AS timestamp))
          AND o.created_at <  DATE_TRUNC('month', CAST(:endDate AS timestamp)) + INTERVAL '1 month'
        GROUP BY 1
    ),
    paid AS (
        SELECT CAST(DATE_TRUNC('month', o.paid_at) AS date) AS month_date, COUNT(*) AS jumlah
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status = 'PAID'
          AND o.paid_at >= DATE_TRUNC('month', CAST(:startDate AS timestamp))
          AND o.paid_at <  DATE_TRUNC('month', CAST(:endDate AS timestamp)) + INTERVAL '1 month'
        GROUP BY 1
    )
    SELECT
        TO_CHAR(m.month_date, 'YYYY-Mon') AS date,
        COALESCE(c.jumlah, 0) AS jumlahOrder,
        COALESCE(pd.jumlah, 0) AS jumlahBayar
    FROM months m
    LEFT JOIN created c ON c.month_date = m.month_date
    LEFT JOIN paid pd ON pd.month_date = m.month_date
    ORDER BY m.month_date
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
        FROM public."order" o
        JOIN public.produk p ON p.id = o.id_produk
        WHERE p.id_workspace = :idWorkspace
          AND o.status = 'PAID'
          AND o.paid_at BETWEEN :startDate AND :endDate
        GROUP BY DATE_TRUNC('month', o.paid_at)
        ORDER BY DATE_TRUNC('month', o.paid_at)
        """, nativeQuery = true)
    List<TotalPendapatanReportView> getMonthlyReportPendapatan(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")LocalDateTime endDate,
            @Param("idWorkspace") Long idWorkspace
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

    /**
     * Kode order aktif sebuah conversation. Kosong bila order tidak ada atau berstatus DELETE —
     * order yang dihapus tidak pernah diperlakukan sebagai pesanan aktif.
     */
    @Query("SELECT o.orderCode FROM Order o WHERE o.id = :id AND COALESCE(o.status, '') <> 'DELETE'")
    Optional<String> findActiveOrderCode(@Param("id") UUID id);

    boolean existsByIdAndStatus(UUID id, String status);

    // Konteks pesanan untuk bot/AI: order DELETE tidak pernah diikutkan.
    @Query("SELECT o.id FROM Order o where o.idConversation = :idConversation AND COALESCE(o.status, '') <> 'DELETE'")
    List<UUID> getOrderIdsByIdConversation(@Param("idConversation") UUID idConversation);

}
