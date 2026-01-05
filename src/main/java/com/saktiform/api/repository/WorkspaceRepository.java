package com.saktiform.api.repository;

import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.workspace.WorkspaceDropdownDto;
import com.saktiform.api.model.workspace.WorkspaceListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long>{
    @Query(
            value = """
        SELECT
            w.id AS idWorkspace,
            w.nama_workspace AS namaWorkspace,
            COALESCE(COUNT(DISTINCT aw.id_account), 0) AS totalUser,
            COALESCE(wa.nomor_whatsapp, '') AS nomorWaba,
            COALESCE(wa.status, '') AS statusWaba
        FROM workspace w
        LEFT JOIN account_workspace aw ON w.id = aw.id_workspace
        LEFT JOIN whatsapp_business_api wa ON w.waba_id = wa.id
        GROUP BY w.id, w.nama_workspace, wa.nomor_whatsapp, wa.status
        ORDER BY w.nama_workspace
        """,
            countQuery = """
        SELECT COUNT(*) 
        FROM (
            SELECT w.id
            FROM workspace w
            LEFT JOIN account_workspace aw ON w.id = aw.id_workspace
            LEFT JOIN whatsapp_business_api wa ON w.waba_id = wa.id
            GROUP BY w.id, w.nama_workspace, wa.nomor_whatsapp, wa.status
        ) AS sub
        """,
            nativeQuery = true
    )
    Page<WorkspaceListDto> getWorkspaceList(Pageable pageable);


    @Query(
            value = """
              Select new com.saktiform.api.model.workspace.WorkspaceDropdownDto(
                ws.id, ws.namaWorkspace
              )
              FROM Workspace ws
              """
    )
    List<WorkspaceDropdownDto> getWorkspaceDropdown();

    Workspace findByWaba_id(UUID wabaId);

}
