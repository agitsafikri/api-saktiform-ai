package com.saktiform.api.repository;

import com.saktiform.api.entity.ChatTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatTemplateRepository extends JpaRepository<ChatTemplate, UUID> {
    void deleteChatTemplateById(UUID id);

    // Query entity (bukan constructor expression) agar kolom JSON media_links ikut terpetakan.
    @Query("""
    SELECT ct
    FROM ChatTemplate ct Where ct.idWorkspace = :idWorkspace
        AND (
            :search IS NULL
            OR ct.namaTemplate LIKE CONCAT('%', :search, '%')
        )
    """)
    Page<ChatTemplate> findListChatTemplate(@Param("idWorkspace") Long idWorkspace, Pageable pageable, @Param("search") String search);

    ChatTemplate getByCategoryAndIdWorkspace(String category, Long idWorkspace);
}