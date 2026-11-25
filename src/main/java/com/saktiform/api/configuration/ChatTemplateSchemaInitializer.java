package com.saktiform.api.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Backfill kolom {@code chat_template.media_links} dari {@code media_link} untuk template yang dibuat
 * sebelum fitur multiple image. Dijalankan pada {@link ApplicationReadyEvent} (setelah {@code ddl-auto}
 * menambah kolom). Idempotent; bila gagal, read tetap fallback ke {@code media_link}.
 * Pola konsisten dengan {@code LabelSchemaInitializer}.
 */
@Component
public class ChatTemplateSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ChatTemplateSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public ChatTemplateSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillMediaLinks() {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE chat_template
                       SET media_links = jsonb_build_array(media_link)
                     WHERE media_links IS NULL
                       AND media_link IS NOT NULL
                       AND media_link <> ''
                    """);
            log.info("Chat template schema: backfill media_links {} baris", updated);
        } catch (Exception e) {
            log.error("Chat template schema: gagal backfill media_links — read tetap fallback ke media_link", e);
        }
    }
}
