package com.saktiform.api.repository;

import com.saktiform.api.entity.BlastMessage;
import com.saktiform.api.model.blast.response.StatusCountProjection;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface BlastMessageRepository extends JpaRepository<BlastMessage, Long> {

    Optional<BlastMessage> findByIdAndIdWorkspace(Long id, Long idWorkspace);

    Optional<BlastMessage> findFirstByProviderMessageId(String providerMessageId);

    List<BlastMessage> findByCampaignIdAndStatus(UUID campaignId, String status);

    long countByCampaignId(UUID campaignId);

    Page<BlastMessage> findByCampaignId(UUID campaignId, Pageable pageable);

    Page<BlastMessage> findByCampaignIdAndStatus(UUID campaignId, String status, Pageable pageable);

    /** Cancel: set semua recipient WAITING → SKIPPED (BR-8). */
    @Modifying
    @Query(value = """
            UPDATE blast_message
               SET status = 'SKIPPED', skipped_at = now(), updated_at = now()
             WHERE campaign_id = :campaignId AND status = 'WAITING'
            """, nativeQuery = true)
    int markWaitingSkipped(@Param("campaignId") UUID campaignId);

    /**
     * Reply detection: recipient campaign aktif untuk nomor & workspace, dalam window.
     */
    @Query(value = """
            SELECT * FROM blast_message
             WHERE id_workspace = :idWorkspace AND phone = :phone
               AND status IN ('SENT','DELIVERED','READ')
               AND sent_at >= :windowStart
             ORDER BY sent_at DESC LIMIT 1
            """, nativeQuery = true)
    Optional<BlastMessage> findRepliable(@Param("idWorkspace") Long idWorkspace,
                                         @Param("phone") String phone,
                                         @Param("windowStart") Instant windowStart);

    /**
     * Generate recipient set-based dengan dedup (BR-6/BR-10). Mengembalikan jumlah baris ter-insert.
     */
    @Modifying
    @Query(value = """
            INSERT INTO blast_message
                (id_workspace, campaign_id, contact_id, phone, name, status, retry_count, waiting_at, created_at)
            SELECT bic.id_workspace, :campaignId, bic.contact_id, bic.normalized_phone, bic.raw_name,
                   'WAITING', 0, now(), now()
              FROM blast_import_contact bic
             WHERE bic.import_id = :importId
               AND bic.category IN (:categories)
            ON CONFLICT (campaign_id, phone) DO NOTHING
            """, nativeQuery = true)
    int generateRecipients(@Param("campaignId") UUID campaignId,
                           @Param("importId") Long importId,
                           @Param("categories") Collection<String> categories);

    @Query(value = """
            SELECT status AS status, COUNT(*) AS cnt
              FROM blast_message WHERE campaign_id = :campaignId GROUP BY status
            """, nativeQuery = true)
    List<StatusCountProjection> countByStatus(@Param("campaignId") UUID campaignId);

    /**
     * Report (FR-14): stream baris per recipient untuk export Excel.
     * Wajib dipanggil dalam @Transactional(readOnly=true) + di-close stream-nya.
     */
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "500"))
    @Query("SELECT m FROM BlastMessage m WHERE m.campaignId = :campaignId ORDER BY m.id ASC")
    Stream<BlastMessage> streamReportRows(@Param("campaignId") UUID campaignId);
}
