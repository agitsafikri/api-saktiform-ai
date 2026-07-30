package com.saktiform.api.service.blast.worker;

import com.saktiform.api.repository.BlastJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mengembalikan job dengan lease kedaluwarsa (worker mati / hang) ke READY agar diklaim ulang
 * (graceful restart, BR-11). Guard idempotency di {@code beginSend} mencegah kirim ganda.
 */
@Component
public class BlastJobReaper {

    private static final Logger log = LoggerFactory.getLogger(BlastJobReaper.class);

    private final BlastJobRepository jobRepository;

    public BlastJobReaper(BlastJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Scheduled(fixedDelayString = "${blast.worker.reaper-interval-ms:30000}")
    @Transactional
    public void reap() {
        try {
            int reclaimed = jobRepository.reapExpired();
            if (reclaimed > 0) {
                log.info("Blast reaper: {} job lease kedaluwarsa dikembalikan ke READY", reclaimed);
            }
        } catch (Exception e) {
            log.error("Blast reaper error", e);
        }
    }
}
