package com.saktiform.api.configuration;

import com.saktiform.api.model.product.formconfig.FieldCategory;
import com.saktiform.api.model.product.formconfig.FormFieldType;
import com.saktiform.api.model.product.formconfig.SystemFormField;
import com.saktiform.api.service.formconfig.FieldKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Migrasi skema &amp; data untuk fitur Konfigurasi Form Produk.
 *
 * <p>Menangani hal-hal yang tidak dapat dihasilkan Hibernate dari anotasi entity:
 * unique index <b>fungsional</b> {@code lower(field_key)}, {@code CHECK constraint},
 * normalisasi nilai {@code NULL}, dan backfill {@code field_key}/{@code field_category}
 * atas baris legacy. Mengikuti pola {@code LabelSchemaInitializer} /
 * {@code BlastSchemaInitializer}.
 *
 * <p>Seluruh langkah <b>idempoten</b> dan aman dijalankan pada setiap startup. Kegagalan
 * satu langkah dicatat sebagai {@code ERROR} namun <b>tidak menggagalkan startup</b> —
 * gangguan layanan penuh demi masalah integritas lokal jauh lebih merugikan.
 */
@Component
public class ProdukFormConfigSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ProdukFormConfigSchemaInitializer.class);

    private final JdbcTemplate jdbc;
    private final FieldKeyGenerator keyGenerator;

    public ProdukFormConfigSchemaInitializer(JdbcTemplate jdbc, FieldKeyGenerator keyGenerator) {
        this.jdbc = jdbc;
        this.keyGenerator = keyGenerator;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrate() {
        step("normalisasi NULL", this::normalizeNulls);
        step("normalisasi tipe_field", this::normalizeFieldTypes);
        step("backfill field_key", this::backfillFieldKeys);
        step("seeding System Field", this::seedSystemFields);
        step("index & constraint", this::ensureIndexesAndConstraints);
        step("verifikasi", this::verify);
    }

    private void step(String name, Runnable action) {
        try {
            action.run();
            log.info("FormConfig migrasi: {} selesai", name);
        } catch (Exception e) {
            log.error("FormConfig migrasi: {} GAGAL — dilanjutkan ke langkah berikutnya", name, e);
        }
    }

    // ── M-2 : normalisasi NULL ──

    private void normalizeNulls() {
        jdbc.update("UPDATE produk_form_config SET orders = 999 WHERE orders IS NULL");
        jdbc.update("UPDATE produk_form_config SET is_mandatory = false WHERE is_mandatory IS NULL");
        jdbc.update("UPDATE produk_form_config SET is_active = true WHERE is_active IS NULL");
        jdbc.update("UPDATE produk_form_config SET created_at = now() WHERE created_at IS NULL");
        jdbc.update("UPDATE produk_form_config SET updated_at = now() WHERE updated_at IS NULL");
        jdbc.update("UPDATE produk_form_config SET label = 'Field' "
                + "WHERE label IS NULL OR btrim(label) = ''");
    }

    // ── M-3 : normalisasi tipe_field legacy ──

    private void normalizeFieldTypes() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, tipe_field FROM produk_form_config "
                        + "WHERE tipe_field IS NULL OR tipe_field <> upper(tipe_field)");
        if (rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> r : rows) {
            String raw = (String) r.get("tipe_field");
            FormFieldType t = FormFieldType.parseLegacy(raw);
            if (t == null) {
                log.warn("FormConfig migrasi: tipe_field '{}' tak dikenal pada id={} -> TEXT",
                        raw, r.get("id"));
                t = FormFieldType.TEXT;
            }
            jdbc.update("UPDATE produk_form_config SET tipe_field = ? WHERE id = ?",
                    t.name(), r.get("id"));
        }
        log.info("FormConfig migrasi: {} baris tipe_field dinormalkan", rows.size());
    }

    // ── M-4 : backfill field_key & field_category ──

    private void backfillFieldKeys() {
        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM produk_form_config WHERE field_key IS NULL", Integer.class);
        if (pending == null || pending == 0) {
            return; // guard biaya: tidak ada pekerjaan, jangan jalankan UPDATE apa pun
        }
        log.info("FormConfig migrasi: {} baris menunggu backfill field_key", pending);

        List<UUID> produkIds = jdbc.queryForList(
                "SELECT DISTINCT id_produk FROM produk_form_config "
                        + "WHERE field_key IS NULL AND id_produk IS NOT NULL",
                UUID.class);

        for (UUID idProduk : produkIds) {
            try {
                backfillOneProduct(idProduk);
            } catch (Exception e) {
                log.error("FormConfig migrasi: backfill gagal untuk produk {}", idProduk, e);
            }
        }
    }

    /**
     * Backfill satu produk.
     *
     * <p>Baris legacy tidak punya identitas, sehingga dipetakan berdasarkan heuristik
     * label. Klausa "System Field belum dipakai" mencegah dua baris legacy dipetakan ke
     * kunci yang sama (mis. produk dengan label "Nama" dan "Nama Lengkap"); baris kedua
     * jatuh menjadi Custom Field sehingga tidak ada data yang hilang.
     */
    private void backfillOneProduct(UUID idProduk) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, label, tipe_field FROM produk_form_config "
                        + "WHERE id_produk = ? ORDER BY orders NULLS LAST, id", idProduk);

        Set<String> usedKeys = new HashSet<>(jdbc.queryForList(
                "SELECT lower(field_key) FROM produk_form_config "
                        + "WHERE id_produk = ? AND field_key IS NOT NULL",
                String.class, idProduk));

        int order = 1;
        for (Map<String, Object> r : rows) {
            Long id = ((Number) r.get("id")).longValue();
            String label = (String) r.get("label");
            String normalized = keyGenerator.normalizeLabel(label);

            Optional<SystemFormField> match = SystemFormField.matchByLabel(normalized);
            String fieldKey;
            FieldCategory category;
            String tipeField = (String) r.get("tipe_field");

            if (match.isPresent() && !usedKeys.contains(match.get().getKey())) {
                SystemFormField sf = match.get();
                fieldKey = sf.getKey();
                category = FieldCategory.SYSTEM;
                tipeField = sf.getType().name();
                jdbc.update("UPDATE produk_form_config SET field_key = ?, field_category = ?, "
                                + "tipe_field = ?, is_mandatory = true, is_active = true, orders = ? "
                                + "WHERE id = ?",
                        fieldKey, category.name(), tipeField, order++, id);
            } else {
                fieldKey = keyGenerator.generate(label, usedKeys);
                category = FieldCategory.CUSTOM;
                // Tipe di luar cakupan (TEXT/TEXTAREA/SELECT) dipetakan ke padanan terdekat
                FormFieldType t = FormFieldType.parseLegacy(tipeField);
                if (t == null || t.isSystemOnly()) {
                    t = FormFieldType.TEXT;
                }
                jdbc.update("UPDATE produk_form_config SET field_key = ?, field_category = ?, "
                                + "tipe_field = ?, orders = ? WHERE id = ?",
                        fieldKey, category.name(), t.name(), order++, id);
            }
            usedKeys.add(fieldKey.toLowerCase(Locale.ROOT));
        }
    }

    // ── M-5 : seeding System Field yang belum ada ──

    private void seedSystemFields() {
        Timestamp now = Timestamp.from(Instant.now());
        int total = 0;
        for (SystemFormField sf : SystemFormField.values()) {
            int inserted = jdbc.update("""
                    INSERT INTO produk_form_config
                        (id_produk, field_key, field_category, tipe_field, label, placeholder,
                         is_mandatory, is_active, orders, created_at, updated_at)
                    SELECT p.id, ?, 'SYSTEM', ?, ?, ?, true, true, ?, ?, ?
                    FROM produk p
                    WHERE NOT EXISTS (
                        SELECT 1 FROM produk_form_config pfc
                        WHERE pfc.id_produk = p.id AND lower(pfc.field_key) = ?
                    )
                    """,
                    sf.getKey(), sf.getType().name(), sf.getDefaultLabel(),
                    sf.getDefaultPlaceholder(), sf.getDefaultSortOrder(), now, now,
                    sf.getKey());
            total += inserted;
        }
        if (total > 0) {
            log.info("FormConfig migrasi: {} baris System Field di-seed", total);
        }
    }

    // ── M-6 : index & constraint ──

    private void ensureIndexesAndConstraints() {
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_pfc_produk_field_key
                    ON produk_form_config (id_produk, lower(field_key))
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_pfc_produk_active_sort
                    ON produk_form_config (id_produk, is_active, orders)
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_pfc_category
                    ON produk_form_config (field_category)
                """);

        addConstraintIfAbsent("produk_form_config", "ck_pfc_category",
                "CHECK (field_category IS NULL OR field_category IN ('SYSTEM','CUSTOM'))");
        addConstraintIfAbsent("produk_form_config", "ck_pfc_system_locked",
                "CHECK (field_category <> 'SYSTEM' OR (is_mandatory = true AND is_active = true))");

        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_ocf_order
                    ON order_custom_field (id_order, sort_order)
                """);
        jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_ocf_produk_field
                    ON order_custom_field (id_produk, field_key)
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_ocf_order_field
                    ON order_custom_field (id_order, field_key)
                """);
    }

    /** PostgreSQL tidak mendukung {@code ADD CONSTRAINT IF NOT EXISTS}. */
    private void addConstraintIfAbsent(String table, String name, String definition) {
        try {
            Integer exists = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE conname = ?", Integer.class, name);
            if (exists != null && exists > 0) {
                return;
            }
            jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " " + definition);
            log.info("FormConfig migrasi: constraint {} dibuat", name);
        } catch (Exception e) {
            log.error("FormConfig migrasi: gagal membuat constraint {} — "
                    + "aturan tetap ditegakkan lapisan aplikasi", name, e);
        }
    }

    // ── M-7 : verifikasi ──

    private void verify() {
        Integer nullKeys = jdbc.queryForObject(
                "SELECT count(*) FROM produk_form_config WHERE field_key IS NULL", Integer.class);
        Integer badProducts = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT p.id FROM produk p
                    LEFT JOIN produk_form_config pfc
                           ON pfc.id_produk = p.id AND pfc.field_category = 'SYSTEM'
                    GROUP BY p.id HAVING count(pfc.id) <> 6
                ) t
                """, Integer.class);

        log.info("FormConfig migrasi: field_key NULL={}, produk tanpa 6 System Field={}",
                nullKeys, badProducts);

        if ((nullKeys != null && nullKeys > 0) || (badProducts != null && badProducts > 0)) {
            log.error("FormConfig migrasi: verifikasi TIDAK bersih — periksa manual. "
                    + "Self-healing akan melengkapi saat konfigurasi produk dibuka.");
        }
    }
}
