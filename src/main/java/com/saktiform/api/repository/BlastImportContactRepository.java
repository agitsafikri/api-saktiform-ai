package com.saktiform.api.repository;

import com.saktiform.api.entity.BlastImportContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface BlastImportContactRepository extends JpaRepository<BlastImportContact, Long> {

    Page<BlastImportContact> findByImportIdAndCategory(Long importId, String category, Pageable pageable);

    long countByImportIdAndCategoryIn(Long importId, Collection<String> categories);

    java.util.Optional<BlastImportContact> findFirstByImportIdAndCategoryInOrderByIdAsc(
            Long importId, Collection<String> categories);

    /**
     * Analisis: tandai DUPLICATE untuk kemunculan ke-2+ nomor ternormalisasi yang sama dalam satu import.
     * Hanya menyentuh baris yang belum INVALID.
     */
    @Modifying
    @Query(value = """
            UPDATE blast_import_contact bic
               SET category = 'DUPLICATE', invalid_reason = 'duplicate in file'
              FROM (
                  SELECT id, ROW_NUMBER() OVER (PARTITION BY normalized_phone ORDER BY id) AS rn
                    FROM blast_import_contact
                   WHERE import_id = :importId
                     AND normalized_phone IS NOT NULL
                     AND category <> 'INVALID'
              ) d
             WHERE bic.id = d.id AND d.rn > 1
            """, nativeQuery = true)
    int markDuplicates(@Param("importId") Long importId);

    /**
     * Analisis: tandai EXISTING via JOIN ke contact (scoped workspace) untuk baris yang masih NEW.
     */
    @Modifying
    @Query(value = """
            UPDATE blast_import_contact bic
               SET category = 'EXISTING', contact_id = c.id
              FROM contact c
             WHERE bic.import_id = :importId
               AND bic.id_workspace = :idWorkspace
               AND bic.category = 'NEW'
               AND c.id_workspace = bic.id_workspace
               AND c.phone_number = bic.normalized_phone
            """, nativeQuery = true)
    int markExistingByWorkspace(@Param("importId") Long importId, @Param("idWorkspace") Long idWorkspace);
}
