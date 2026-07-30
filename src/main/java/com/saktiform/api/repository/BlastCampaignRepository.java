package com.saktiform.api.repository;

import com.saktiform.api.entity.BlastCampaign;
import com.saktiform.api.model.blast.response.CampaignListProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlastCampaignRepository extends JpaRepository<BlastCampaign, UUID> {

    Optional<BlastCampaign> findByIdAndIdWorkspace(UUID id, Long idWorkspace);

    @Query(value = """
            SELECT bc.id AS id, bc.name AS name, bc.status AS status,
                   bc.total_recipient AS totalRecipient, bc.count_sent AS countSent,
                   bc.count_failed AS countFailed, bc.count_replied AS countReplied,
                   bc.created_at AS createdAtRaw
              FROM blast_campaign bc
             WHERE bc.id_workspace = :idWorkspace
               AND (:status IS NULL OR bc.status = :status)
               AND (:keyword IS NULL OR bc.name ILIKE CONCAT('%', :keyword, '%'))
             ORDER BY bc.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM blast_campaign bc
             WHERE bc.id_workspace = :idWorkspace
               AND (:status IS NULL OR bc.status = :status)
               AND (:keyword IS NULL OR bc.name ILIKE CONCAT('%', :keyword, '%'))
            """, nativeQuery = true)
    Page<CampaignListProjection> search(@Param("idWorkspace") Long idWorkspace,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword,
                                        Pageable pageable);

    @Query(value = "SELECT id FROM blast_campaign WHERE status = 'RUNNING'", nativeQuery = true)
    List<UUID> findRunningIds();

    // ---- Counter atomik (increment relatif, cegah lost update) ----

    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_waiting = count_waiting - 1, count_sending = count_sending + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onSending(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_sending = count_sending - 1, count_sent = count_sent + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onSent(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_sending = count_sending - 1, count_failed = count_failed + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onFailed(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_replied = count_replied + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onReplied(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_sending = count_sending - 1, count_waiting = count_waiting + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onRequeue(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_waiting = count_waiting - 1, count_skipped = count_skipped + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onSkipped(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE blast_campaign SET status = 'FINISHED', finished_at = now(), updated_at = now()
             WHERE id = :id AND status = 'RUNNING' AND count_waiting = 0 AND count_sending = 0
            """, nativeQuery = true)
    int markFinishedIfDone(@Param("id") UUID id);

    /** Retry manual: FAILED → WAITING (failed--, waiting++). */
    @Modifying
    @Query(value = """
            UPDATE blast_campaign
               SET count_failed = count_failed - 1, count_waiting = count_waiting + 1, updated_at = now()
             WHERE id = :id""", nativeQuery = true)
    void onRetryFromFailed(@Param("id") UUID id);

    /** Buka kembali campaign FINISHED saat ada retry (→ RUNNING). */
    @Modifying
    @Query(value = """
            UPDATE blast_campaign SET status = 'RUNNING', finished_at = NULL, updated_at = now()
             WHERE id = :id AND status = 'FINISHED'""", nativeQuery = true)
    int onReopen(@Param("id") UUID id);
}
