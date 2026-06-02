package com.saktiform.api.repository;

import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.entity.Workspace;
import com.saktiform.api.model.whatsapp.AvailableWhatsappResponse;
import com.saktiform.api.model.whatsapp.WabaListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WhatsappBusinessApiRepository extends JpaRepository<WhatsappBusinessApi, UUID> {
    @Query("SELECT w.port FROM WhatsappBusinessApi w ")
    List<Integer> findAllUsedPorts();

    WhatsappBusinessApi findByNomorWhatsapp(String nomorWhatsapp);

    @Query("""
        Select new com.saktiform.api.model.whatsapp.WabaListDto(
                    a.id, a.nomorWhatsapp, a.status, wp.namaWorkspace
                )
        From WhatsappBusinessApi as a
                Left join Workspace as wp on wp.wabaId = a.id
                WHERE (:search IS NULL OR CONCAT('+',a.nomorWhatsapp)  ILIKE  CONCAT('%', :search, '%'))
                OR (:search IS NULL OR  a.status ILIKE  CONCAT('%', :search, '%'))
                OR (:search IS NULL OR  wp.namaWorkspace  ILIKE  CONCAT('%', :search, '%'))
        """)
    Page<WabaListDto>getListWaba(@Param("search")String search, Pageable pageable);

    WhatsappBusinessApi findByPort(Integer port);

    Object getWhatsappBusinessApiById(UUID id);

    WhatsappBusinessApi findByDeviceId(String deviceId);

    @Query("""
    Select new com.saktiform.api.model.whatsapp.AvailableWhatsappResponse(
            waba.id,
            waba.nomorWhatsapp
        )  FROM WhatsappBusinessApi waba
        LEFT OUTER JOIN Workspace wp
            ON waba.id = wp.wabaId
            WHERE waba.status = "CONNECTED"
    """)
    List<AvailableWhatsappResponse> getAvailableWhatsapp();

    @Query("""
    SELECT w from Workspace w where w.wabaId = :idWaba 
    """)
    Workspace wabaIsUsed(@Param("idWaba") UUID idWaba);


}