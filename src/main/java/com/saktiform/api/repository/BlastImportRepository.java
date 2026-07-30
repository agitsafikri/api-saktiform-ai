package com.saktiform.api.repository;

import com.saktiform.api.entity.BlastImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlastImportRepository extends JpaRepository<BlastImport, Long> {

    Optional<BlastImport> findByIdAndIdWorkspace(Long id, Long idWorkspace);

    Page<BlastImport> findByIdWorkspaceOrderByCreatedAtDesc(Long idWorkspace, Pageable pageable);
}
