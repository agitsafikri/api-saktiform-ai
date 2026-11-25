package com.saktiform.api.repository;

import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.workspace.WorkspaceDropdownDto;
import com.saktiform.api.model.workspace.WorkspaceListDto;
import com.saktiform.api.model.workspace.WorkspaceOrderSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long>{

    /**
     * Filter search dipakai bersama oleh query data dan count. Sebelumnya filter ini
     * berada di klausa ON milik LEFT JOIN WABA pada query data, sehingga search tidak
     * menyaring workspace sama sekali (hanya mengosongkan kolom WABA) dan total count
     * tidak sama dengan baris yang tampil.
     */
    String WORKSPACE_LIST_FILTER = """
            WHERE (
                :search IS NULL
                OR w.nama_workspace ILIKE CONCAT('%', :search, '%')
                OR wa.nomor_whatsapp ILIKE CONCAT('%', :search, '%')
                OR wa.status ILIKE CONCAT('%', :search, '%')
            )
            """;

    @Query(
            value = """
            SELECT
                w.id AS idWorkspace,
                w.nama_workspace AS namaWorkspace,
                (SELECT COUNT(*) FROM account_workspace aw WHERE aw.id_workspace = w.id) AS totalUser,
                COALESCE(wa.nomor_whatsapp, '') AS nomorWaba,
                COALESCE(wa.status, '') AS statusWaba
            FROM workspace w
            LEFT JOIN whatsapp_business_api wa ON w.waba_id = wa.id
            """ + WORKSPACE_LIST_FILTER + """
            ORDER BY w.nama_workspace
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM workspace w
            LEFT JOIN whatsapp_business_api wa ON w.waba_id = wa.id
            """ + WORKSPACE_LIST_FILTER,
            nativeQuery = true
    )
    Page<WorkspaceListDto> getWorkspaceList(@Param("search") String search, Pageable pageable);


    @Query(
            value = """
              Select new com.saktiform.api.model.workspace.WorkspaceDropdownDto(
                ws.id, ws.namaWorkspace
              )
              FROM Workspace ws
              """
    )
    List<WorkspaceDropdownDto> getWorkspaceDropdown();

    Workspace findByWabaId(UUID wabaId);

    /**
     * Total / paid / unpaid order workspace dalam satu scan (sebelumnya tiga query terpisah).
     * Filter tanggal membandingkan kolom secara langsung agar index
     * order(id_produk, created_at) bisa dipakai.
     * Order berstatus DELETE dikecualikan dari seluruh hitungan; IS DISTINCT FROM
     * dipakai agar order dengan status NULL tetap terhitung.
     */
    @Query(value = """
        SELECT
            COUNT(*) AS totalOrder,
            COUNT(*) FILTER (WHERE ord.status = 'PAID') AS paidOrder,
            COUNT(*) FILTER (WHERE ord.status <> 'PAID') AS unpaidOrder
        FROM public."order" ord
        JOIN produk prod ON ord.id_produk = prod.id
        WHERE prod.id_workspace = :idWorkspace
          AND ord.status IS DISTINCT FROM 'DELETE'
          AND (CAST(:createdAtStart AS timestamp) IS NULL
               OR ord.created_at >= CAST(:createdAtStart AS timestamp))
          AND (CAST(:createdAtEnd AS timestamp) IS NULL
               OR ord.created_at <= CAST(:createdAtEnd AS timestamp))
    """,
    nativeQuery = true)
    WorkspaceOrderSummaryView getOrderSummaryByWorkspace(@Param("idWorkspace") Long idWorkspace,
                                                         @Param("createdAtStart") LocalDateTime startDate,
                                                         @Param("createdAtEnd") LocalDateTime endDate);

    @Query(value = """
        Select waba From WhatsappBusinessApi waba where waba.id = :wabaId
    """)
    WhatsappBusinessApi findWabaById(@Param("wabaId") UUID wabaId);


}
