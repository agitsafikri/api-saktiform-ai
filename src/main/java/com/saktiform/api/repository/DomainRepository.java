package com.saktiform.api.repository;

import com.saktiform.api.entity.Domain;
import com.saktiform.api.model.domain.DomainDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DomainRepository extends JpaRepository<Domain, Long> {

    @Query("""
        SELECT new com.saktiform.api.model.domain.DomainDto(
            d.id,
            d.domain
            ) FROM Domain d WHERE d.workspace.id = ?1
    """)
    Page<DomainDto> getDomainList(Long workspaceId, Pageable pageable);

    @Query("""
         SELECT new com.saktiform.api.model.domain.DomainDto(
            d.id,
            d.domain
            ) FROM Domain d WHERE d.workspace.id = ?1
    """)
    List<DomainDto> getDomainByWorkspaceId(Long workspaceId);
}