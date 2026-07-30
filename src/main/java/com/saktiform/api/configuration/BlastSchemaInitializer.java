package com.saktiform.api.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Membuat objek skema Blast yang tidak dapat dihasilkan Hibernate maupun Flyway.
 *
 * <p>Partial index klaim job (`WHERE status='READY'`) tidak bisa dideklarasikan via @Table @Index
 * (Hibernate tidak mendukung partial index) dan tidak bisa via Flyway (Flyway berjalan SEBELUM
 * Hibernate membuat tabel blast_* → migrasi gagal). Dijalankan pada ApplicationReadyEvent yang
 * dipastikan berlangsung setelah ddl-auto membuat tabel. Idempotent (IF NOT EXISTS).
 */
@Component
public class BlastSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(BlastSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public BlastSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_job_claim
                        ON blast_job (status, available_at, priority DESC, id)
                        WHERE status = 'READY'
                    """);
            log.info("Blast schema: partial index idx_job_claim ensured");
        } catch (Exception e) {
            log.error("Blast schema: gagal membuat idx_job_claim — worker tetap jalan, " +
                    "tapi performa klaim job tidak optimal", e);
        }
    }
}
