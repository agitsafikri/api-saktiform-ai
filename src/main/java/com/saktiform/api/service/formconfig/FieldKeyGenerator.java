package com.saktiform.api.service.formconfig;

import com.saktiform.api.model.product.formconfig.SystemFormField;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Membangkitkan {@code field_key} Custom Field dari label, dan menormalkan label untuk
 * keperluan pencocokan heuristik saat backfill.
 *
 * <p>Kunci dibangkitkan sistem — bukan diisi pengguna — agar pengguna non-teknis tidak
 * perlu memahami konsep kunci teknis, sekaligus menjamin keunikan per produk.
 */
@Component
public class FieldKeyGenerator {

    /** Sisakan ruang untuk sufiks penyelesaian tabrakan pada batas kolom 64 karakter. */
    private static final int MAX_BASE_LENGTH = 56;
    private static final String FALLBACK = "field";

    /**
     * Normalisasi label untuk pencocokan heuristik: lowercase, tanpa diakritik,
     * tanpa tanda baca, spasi tunggal.
     */
    public String normalizeLabel(String label) {
        if (label == null) {
            return "";
        }
        return Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Slugify label menjadi {@code field_key} yang unik pada produk tersebut.
     *
     * <p>Langkah: normalisasi Unicode ke ASCII, ganti karakter non-alfanumerik dengan
     * garis bawah, pangkas, potong ke {@value #MAX_BASE_LENGTH} karakter, pastikan
     * diawali huruf, hindari kata terlarang, lalu selesaikan tabrakan dengan sufiks
     * numerik.
     *
     * @param usedKeys field key yang sudah dipakai pada produk yang sama
     */
    public String generate(String label, Set<String> usedKeys) {
        String base = Normalizer.normalize(label == null ? "" : label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH).replaceAll("_+$", "");
        }
        if (base.isBlank()) {
            base = FALLBACK;
        }
        if (!base.matches("^[a-z].*")) {
            base = FALLBACK + "_" + base;
        }
        if (SystemFormField.RESERVED_KEYS.contains(base)) {
            base = "custom_" + base;
        }

        Set<String> lowered = usedKeys == null
                ? Set.of()
                : usedKeys.stream()
                        .filter(k -> k != null)
                        .map(k -> k.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());

        String candidate = base;
        int suffix = 2;
        while (lowered.contains(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }
}
