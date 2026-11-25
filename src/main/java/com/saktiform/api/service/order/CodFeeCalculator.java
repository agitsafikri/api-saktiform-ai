package com.saktiform.api.service.order;

import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.Order.JenisPembayaran;
import com.saktiform.api.model.product.formconfig.FormConfigErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Perhitungan biaya COD (persentase untuk kurir) dari {@code ProdukPembayaran.config}.
 * Lihat {@code docs/prd/cod-fee.md}.
 *
 * <p>Key config: {@code tipeBiaya}, {@code persentaseBiaya}, serta opsional
 * {@code minimalBiaya} dan {@code maksimalBiaya}.
 *
 * <p>Murni tanpa akses database. Config yang tidak valid saat dihitung dianggap
 * biaya 0 (checkout tidak boleh gagal karena config); validasi ketat dilakukan saat
 * produk disimpan lewat {@link #validateConfig}.
 */
@Slf4j
public final class CodFeeCalculator {

    public static final String KEY_TIPE = "tipeBiaya";
    public static final String KEY_PERSENTASE = "persentaseBiaya";
    public static final String KEY_MINIMAL = "minimalBiaya";
    public static final String KEY_MAKSIMAL = "maksimalBiaya";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Dasar perhitungan biaya COD. */
    public enum TipeBiaya {
        /** harga − diskon */
        HANYA_PRODUK("hanya-produk"),
        /** harga − diskon + tarif ongkir */
        PRODUK_PENGIRIMAN("produk-pengiriman"),
        /** tarif ongkir */
        HANYA_PENGIRIMAN("hanya-pengiriman");

        private final String value;

        TipeBiaya(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        static TipeBiaya from(Object raw) {
            if (raw == null) {
                return null;
            }
            String normalized = raw.toString().trim().toLowerCase(Locale.ROOT);
            for (TipeBiaya tipe : values()) {
                if (tipe.value.equals(normalized)) {
                    return tipe;
                }
            }
            return null;
        }
    }

    private CodFeeCalculator() {
    }

    /**
     * Menghitung biaya COD.
     *
     * @param pembayaran  metode pembayaran order ({@code COD} / lainnya)
     * @param config      snapshot config pembayaran; boleh null
     * @param harga       harga varian
     * @param diskon      diskon order; null dianggap 0
     * @param tarifOngkir tarif ongkir murni, tanpa biaya COD
     * @return biaya COD dalam rupiah, tidak pernah negatif; 0 bila tidak berlaku
     */
    public static long calculate(String pembayaran, Map<String, Object> config,
                                 Long harga, Long diskon, Long tarifOngkir) {
        if (!JenisPembayaran.COD.name().equalsIgnoreCase(pembayaran) || config == null || config.isEmpty()) {
            return 0;
        }
        if (config.get(KEY_TIPE) == null && config.get(KEY_PERSENTASE) == null) {
            // COD tanpa pengaturan biaya.
            return 0;
        }

        TipeBiaya tipe = TipeBiaya.from(config.get(KEY_TIPE));
        BigDecimal persen = parseNumber(config.get(KEY_PERSENTASE));
        BigDecimal minimal = parseNumber(config.get(KEY_MINIMAL));
        BigDecimal maksimal = parseNumber(config.get(KEY_MAKSIMAL));

        if (tipe == null || persen == null || persen.signum() < 0
                || isInvalidOptional(config, KEY_MINIMAL, minimal)
                || isInvalidOptional(config, KEY_MAKSIMAL, maksimal)) {
            log.warn("Config biaya COD tidak valid, biaya dianggap 0. config={}", config);
            return 0;
        }

        BigDecimal hargaSetelahDiskon = BigDecimal.valueOf(nz(harga) - nz(diskon));
        BigDecimal ongkir = BigDecimal.valueOf(nz(tarifOngkir));
        BigDecimal dasar = switch (tipe) {
            case HANYA_PRODUK -> hargaSetelahDiskon;
            case PRODUK_PENGIRIMAN -> hargaSetelahDiskon.add(ongkir);
            case HANYA_PENGIRIMAN -> ongkir;
        };
        if (dasar.signum() < 0) {
            dasar = BigDecimal.ZERO;
        }

        BigDecimal mentah = dasar.multiply(persen).divide(HUNDRED, 10, RoundingMode.HALF_UP);
        // Bulatkan ke atas ke kelipatan Rp100, sebelum batas min/maks.
        BigDecimal biaya = mentah.divide(HUNDRED, 0, RoundingMode.CEILING).multiply(HUNDRED);

        if (minimal != null && biaya.compareTo(minimal) < 0) {
            biaya = minimal;
        }
        if (maksimal != null && biaya.compareTo(maksimal) > 0) {
            biaya = maksimal;
        }
        return biaya.setScale(0, RoundingMode.CEILING).max(BigDecimal.ZERO).longValueExact();
    }

    /**
     * Validasi config biaya COD saat produk disimpan.
     *
     * @param field prefix nama field untuk {@link ErrorDto}, misalnya {@code pembayaran[0].config}
     * @return daftar pelanggaran; kosong bila valid atau config tidak memuat pengaturan biaya
     */
    public static List<ErrorDto> validateConfig(String field, Map<String, Object> config) {
        List<ErrorDto> errors = new ArrayList<>();
        if (config == null || (config.get(KEY_TIPE) == null && config.get(KEY_PERSENTASE) == null
                && config.get(KEY_MINIMAL) == null && config.get(KEY_MAKSIMAL) == null)) {
            return errors;
        }

        if (TipeBiaya.from(config.get(KEY_TIPE)) == null) {
            errors.add(error(field + "." + KEY_TIPE,
                    "Tipe biaya COD harus salah satu dari: hanya-produk, produk-pengiriman, hanya-pengiriman."));
        }

        BigDecimal persen = parseNumber(config.get(KEY_PERSENTASE));
        if (persen == null || persen.signum() < 0 || persen.compareTo(HUNDRED) > 0) {
            errors.add(error(field + "." + KEY_PERSENTASE,
                    "Persentase biaya COD harus berupa angka antara 0 dan 100."));
        }

        BigDecimal minimal = parseNumber(config.get(KEY_MINIMAL));
        BigDecimal maksimal = parseNumber(config.get(KEY_MAKSIMAL));
        boolean minimalValid = !isInvalidOptional(config, KEY_MINIMAL, minimal);
        boolean maksimalValid = !isInvalidOptional(config, KEY_MAKSIMAL, maksimal);
        if (!minimalValid) {
            errors.add(error(field + "." + KEY_MINIMAL, "Minimal biaya COD harus berupa angka bulat >= 0."));
        }
        if (!maksimalValid) {
            errors.add(error(field + "." + KEY_MAKSIMAL, "Maksimal biaya COD harus berupa angka bulat >= 0."));
        }
        if (minimalValid && maksimalValid && minimal != null && maksimal != null
                && minimal.compareTo(maksimal) > 0) {
            errors.add(error(field + "." + KEY_MINIMAL,
                    "Minimal biaya COD tidak boleh lebih besar dari maksimal biaya COD."));
        }
        return errors;
    }

    /**
     * Config pembayaran yang aman disalin saat copy produk: bila {@code pembayaran} adalah COD
     * dan pengaturan biayanya tidak lolos {@link #validateConfig}, keempat key biaya dibuang
     * (biaya COD nonaktif pada produk salinan). Key lain, misalnya {@code deskripsi}, tetap ada.
     *
     * @return config asli bila valid / bukan COD / null; selain itu salinan tanpa key biaya
     */
    public static Map<String, Object> sanitizeForCopy(String pembayaran, Map<String, Object> config) {
        if (config == null || !JenisPembayaran.COD.name().equalsIgnoreCase(pembayaran)
                || validateConfig("config", config).isEmpty()) {
            return config;
        }
        log.warn("Config biaya COD tidak valid dibuang saat copy produk. config={}", config);
        Map<String, Object> cleaned = new java.util.HashMap<>(config);
        cleaned.remove(KEY_TIPE);
        cleaned.remove(KEY_PERSENTASE);
        cleaned.remove(KEY_MINIMAL);
        cleaned.remove(KEY_MAKSIMAL);
        return cleaned;
    }

    // ── helpers ──

    /** Key opsional dianggap tidak valid bila diisi tetapi bukan angka bulat >= 0. */
    private static boolean isInvalidOptional(Map<String, Object> config, String key, BigDecimal parsed) {
        if (isBlank(config.get(key))) {
            return false;
        }
        return parsed == null || parsed.signum() < 0 || parsed.stripTrailingZeros().scale() > 0;
    }

    /** Menerima Number JSON maupun string numerik ("2500", "2500.0", "2.5"). */
    static BigDecimal parseNumber(Object raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            if (raw instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(Object raw) {
        return raw == null || raw.toString().isBlank();
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    private static ErrorDto error(String field, String message) {
        return new ErrorDto(field, message, FormConfigErrorCode.INVALID_COD_FEE_CONFIG, null);
    }
}
