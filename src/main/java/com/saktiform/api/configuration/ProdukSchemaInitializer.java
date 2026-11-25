package com.saktiform.api.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Membuat index fungsional {@code lower(url_checkout)} pada tabel {@code produk}.
 *
 * <p>Dipakai {@code ProdukRepository.findByUrlCheckout} (halaman checkout publik dan copy produk)
 * yang membandingkan {@code lower(url_checkout) = lower(:url)}; tanpa index ini query tersebut
 * memindai seluruh tabel produk lintas tenant. Index fungsional tidak dapat dideklarasikan via
 * {@code @Table @Index}, jadi dibuat di sini setelah {@code ddl-auto} selesai.
 * Idempotent ({@code IF NOT EXISTS}). Pola konsisten dengan {@code LabelSchemaInitializer}.
 */
@Component
public class ProdukSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ProdukSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public ProdukSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_produk_lower_url_checkout
                        ON produk (lower(url_checkout))
                    """);
            log.info("Produk schema: index idx_produk_lower_url_checkout ensured");
        } catch (Exception e) {
            log.error("Produk schema: gagal membuat index lower(url_checkout) — " +
                    "lookup checkout akan memakai full scan", e);
        }
    }
}
