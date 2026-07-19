package com.saktiform.api.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Membuat unique index case-insensitive untuk nama label per workspace.
 *
 * <p>Index fungsional {@code lower(name)} tidak dapat dideklarasikan via {@code @Table @Index}
 * (Hibernate tidak mendukung functional index). Dijalankan pada {@link ApplicationReadyEvent} yang
 * dipastikan berlangsung setelah {@code ddl-auto} membuat tabel {@code conversation_label}.
 * Idempotent ({@code IF NOT EXISTS}). Pola konsisten dengan {@code BlastSchemaInitializer}.
 */
@Component
public class LabelSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(LabelSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public LabelSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_conversation_label_ws_name
                        ON conversation_label (id_workspace, lower(name))
                    """);
            log.info("Label schema: unique index uq_conversation_label_ws_name ensured");
        } catch (Exception e) {
            log.error("Label schema: gagal membuat unique index nama label — " +
                    "keunikan nama label per workspace tidak terjaga di level DB", e);
        }
    }
}
