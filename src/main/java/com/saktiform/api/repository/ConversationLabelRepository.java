package com.saktiform.api.repository;

import com.saktiform.api.entity.ConversationLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationLabelRepository extends JpaRepository<ConversationLabel, Long> {

    Optional<ConversationLabel> findByIdAndIdWorkspace(Long id, Long idWorkspace);

    List<ConversationLabel> findByIdWorkspaceOrderByNameAsc(Long idWorkspace);

    /** Semua label milik workspace yang id-nya termasuk daftar (validasi assign all-or-nothing). */
    List<ConversationLabel> findByIdWorkspaceAndIdIn(Long idWorkspace, Collection<Long> ids);

    /** Cek duplikat nama case-insensitive per workspace (excludeId untuk melewati diri sendiri saat update). */
    @Query("""
            SELECT COUNT(l) > 0 FROM ConversationLabel l
             WHERE l.idWorkspace = :idWorkspace
               AND lower(l.name) = lower(:name)
               AND (:excludeId IS NULL OR l.id <> :excludeId)
            """)
    boolean existsByWorkspaceAndName(@Param("idWorkspace") Long idWorkspace,
                                     @Param("name") String name,
                                     @Param("excludeId") Long excludeId);
}
