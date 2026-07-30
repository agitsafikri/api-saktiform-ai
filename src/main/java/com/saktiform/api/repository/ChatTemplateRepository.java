package com.saktiform.api.repository;

import com.saktiform.api.entity.ChatTemplate;
import com.saktiform.api.model.template.ChatTemplateListDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatTemplateRepository extends JpaRepository<ChatTemplate, UUID> {
    void deleteChatTemplateById(UUID id);

    @Query("""
    SELECT new com.saktiform.api.model.template.ChatTemplateListDto(
        ct.id,
        ct.namaTemplate,
        ct.category,
        ct.mediaLink
    )
    FROM ChatTemplate ct Where ct.idWorkspace = :idWorkspace
    """)
    Page<ChatTemplateListDto> getListChatTemplate(@Param("idWorkspace") Long idWorkspace, Pageable pageable);

    ChatTemplate getByCategoryAndIdWorkspace(String category, Long idWorkspace);
}