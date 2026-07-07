    package com.saktiform.api.repository;

import com.saktiform.api.entity.BlastJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BlastJobRepository extends JpaRepository<BlastJob, Long> {

    /**
     * CLAIM (langkah 1): id job READY siap proses untuk campaign RUNNING, lewati yang terkunci.
     * Harus dijalankan dalam transaksi yang sama dengan {@link #claim} (lock dilepas saat commit).
     */
    @Query(value = """
            SELECT j.id FROM blast_job j
             WHERE j.status = 'READY' AND j.available_at <= now()
               AND j.campaign_id IN (SELECT id FROM blast_campaign WHERE status IN ('RUNNING','QUEUED'))
             ORDER BY j.priority DESC, j.id ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("batchSize") int batchSize);

    /**
     * CLAIM (langkah 2): tandai job CLAIMED + lease. Status guard mencegah klaim ganda.
     */
    @Modifying
    @Query(value = """
            UPDATE blast_job
               SET status = 'CLAIMED', locked_by = :workerId,
                   locked_until = now() + (:leaseMs || ' milliseconds')::interval, updated_at = now()
             WHERE id IN (:ids) AND status = 'READY'
            """, nativeQuery = true)
    int claim(@Param("ids") List<Long> ids, @Param("workerId") String workerId, @Param("leaseMs") long leaseMs);

    /**
     * Generate queue idempotent (BR-13).
     */
    @Modifying
    @Query(value = """
            INSERT INTO blast_job
                (id_workspace, campaign_id, message_id, status, attempt, max_attempts, priority,
                 dedup_key, available_at, created_at)
            SELECT m.id_workspace, :campaignId, m.id, 'READY', 1, :maxAttempts, 0,
                   :campaignId || ':' || m.id || ':1', now(), now()
              FROM blast_message m
             WHERE m.campaign_id = :campaignId AND m.status = 'WAITING'
            ON CONFLICT (message_id, attempt) DO NOTHING
            """, nativeQuery = true)
    int generateQueue(@Param("campaignId") UUID campaignId, @Param("maxAttempts") int maxAttempts);

    /**
     * Reaper: kembalikan lease kedaluwarsa ke READY (graceful restart, BR-11).
     */
    @Modifying
    @Query(value = """
            UPDATE blast_job
               SET status = 'READY', locked_by = NULL, locked_until = NULL, updated_at = now()
             WHERE status IN ('CLAIMED','PROCESSING') AND locked_until < now()
            """, nativeQuery = true)
    int reapExpired();

    /**
     * Cancel: batalkan job pending campaign (BR-8).
     */
    @Modifying
    @Query(value = """
            UPDATE blast_job SET status = 'CANCELLED', updated_at = now()
             WHERE campaign_id = :campaignId AND status IN ('READY','RETRYING')
            """, nativeQuery = true)
    int cancelPending(@Param("campaignId") UUID campaignId);

    /** Attempt tertinggi untuk sebuah message (untuk retry manual → attempt berikutnya unik). */
    @Query("SELECT MAX(j.attempt) FROM BlastJob j WHERE j.messageId = :messageId")
    Integer findMaxAttemptByMessageId(@Param("messageId") Long messageId);
}
