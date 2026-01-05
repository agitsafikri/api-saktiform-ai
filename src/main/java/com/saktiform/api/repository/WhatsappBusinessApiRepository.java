package com.saktiform.api.repository;

import com.saktiform.api.entity.WhatsappBusinessApi;
import com.saktiform.api.model.whatsapp.WabaListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
                Left join Workspace as wp on wp.waba_id = a.id
        """)
    Page<WabaListDto>getListWaba(Pageable pageable);

    WhatsappBusinessApi findByPort(Integer port);

    Object getWhatsappBusinessApiById(UUID id);
}