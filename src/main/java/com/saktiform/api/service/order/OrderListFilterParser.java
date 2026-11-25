package com.saktiform.api.service.order;

import com.saktiform.api.model.ErrorDto;
import com.saktiform.api.model.Order.JenisPembayaran;
import com.saktiform.api.model.Order.OrderFilterErrorCode;
import com.saktiform.api.model.Order.OrderListFilter;
import com.saktiform.api.model.Order.OrderStatus;
import com.saktiform.api.util.ValidationException;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Mengubah parameter filter mentah (string dipisah koma) menjadi {@link OrderListFilter}.
 * Lihat docs/prd/order-multi-filter.md §5.2 dan §6.
 *
 * <p>Semua pelanggaran dikumpulkan lalu dilempar sekaligus sebagai satu
 * {@link ValidationException}. Murni tanpa akses database.
 */
public final class OrderListFilterParser {

    /** Batas jumlah nilai per filter, menjaga URL di bawah batas header Tomcat (±8 KB). */
    public static final int MAX_VALUES = 500;

    /**
     * Batas jumlah ID produk. UUID 36 karakter, sehingga 100 nilai ≈ 3,7 KB dan URL
     * tetap aman di bawah batas header Tomcat (docs/prd/order-product-filter.md).
     */
    public static final int MAX_PRODUK_VALUES = 100;

    /** Bentuk kanonik UUID 8-4-4-4-12; {@link UUID#fromString} sendiri terlalu longgar. */
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private OrderListFilterParser() {
    }

    public static OrderListFilter parse(String idProvinsi, String idKota, String idKecamatan,
                                        String status, String jenisPembayaran, String idProduk) {
        List<ErrorDto> errors = new ArrayList<>();

        Set<Integer> provinsi = parseIds("idProvinsi", "provinsi", idProvinsi, errors);
        Set<Integer> kota = parseIds("idKota", "kota", idKota, errors);
        Set<Integer> kecamatan = parseIds("idKecamatan", "kecamatan", idKecamatan, errors);
        Set<String> statuses = parseEnum("status", "status", status, OrderStatus.class, errors);
        Set<String> pembayaran = parseEnum("jenisPembayaran", "jenis pembayaran", jenisPembayaran,
                JenisPembayaran.class, errors);
        Set<UUID> produk = parseUuids("idProduk", "produk", idProduk, errors);

        // Aturan lokasi bertingkat (V-4..V-6).
        if (provinsi.size() > 1 && !kota.isEmpty()) {
            errors.add(error("idKota", OrderFilterErrorCode.FILTER_LOCATION_CONFLICT,
                    "Kota tidak dapat difilter bila lebih dari satu provinsi dipilih."));
        }
        if (provinsi.size() > 1 && !kecamatan.isEmpty()) {
            errors.add(error("idKecamatan", OrderFilterErrorCode.FILTER_LOCATION_CONFLICT,
                    "Kecamatan tidak dapat difilter bila lebih dari satu provinsi dipilih."));
        } else if (kota.size() > 1 && !kecamatan.isEmpty()) {
            errors.add(error("idKecamatan", OrderFilterErrorCode.FILTER_LOCATION_CONFLICT,
                    "Kecamatan tidak dapat difilter bila lebih dari satu kota dipilih."));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new OrderListFilter(provinsi, kota, kecamatan, statuses, pembayaran, produk);
    }

    /**
     * Filter {@code jenisPembayaran} satu nilai untuk list percakapan chat
     * ({@code GET /conversation/assigned|unassigned}).
     *
     * <ul>
     *   <li>null / kosong / hanya spasi → {@code null} (tanpa filter).</li>
     *   <li>Nama enum {@link JenisPembayaran}, tidak peka huruf → nama enum UPPERCASE.</li>
     *   <li>Nilai tidak dikenal, atau lebih dari satu nilai → {@link ValidationException}
     *       ({@code INVALID_FILTER_VALUE}).</li>
     * </ul>
     */
    public static String parseSingleJenisPembayaran(String raw) {
        List<ErrorDto> errors = new ArrayList<>();
        Set<String> values = parseEnum("jenisPembayaran", "jenis pembayaran", raw, JenisPembayaran.class, errors);
        if (errors.isEmpty() && values.size() > 1) {
            errors.add(error("jenisPembayaran", OrderFilterErrorCode.INVALID_FILTER_VALUE,
                    "Filter jenis pembayaran hanya menerima satu nilai."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return values.isEmpty() ? null : values.iterator().next();
    }

    // ── helpers ──

    private static Set<UUID> parseUuids(String field, String label, String raw, List<ErrorDto> errors) {
        return parseValues(field, label, raw, MAX_PRODUK_VALUES, errors,
                token -> UUID_PATTERN.matcher(token).matches() ? UUID.fromString(token) : null);
    }

    private static Set<Integer> parseIds(String field, String label, String raw, List<ErrorDto> errors) {
        return parseValues(field, label, raw, MAX_VALUES, errors, token -> {
            try {
                int value = Integer.parseInt(token);
                return value > 0 ? value : null;
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    private static <E extends Enum<E>> Set<String> parseEnum(String field, String label, String raw,
                                                             Class<E> type, List<ErrorDto> errors) {
        Set<String> allowed = new HashSet<>();
        for (E constant : type.getEnumConstants()) {
            allowed.add(constant.name());
        }
        return parseValues(field, label, raw, MAX_VALUES, errors, token -> {
            String upper = token.toUpperCase(Locale.ROOT);
            return allowed.contains(upper) ? upper : null;
        });
    }

    /**
     * Memecah string koma: trim, buang token kosong & duplikat. Token yang gagal
     * dikonversi ({@code converter} mengembalikan null) dilaporkan sebagai INVALID_FILTER_VALUE.
     */
    private static <T> Set<T> parseValues(String field, String label, String raw, int maxValues,
                                          List<ErrorDto> errors, Function<String, T> converter) {
        Set<T> result = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }

        List<String> invalid = new ArrayList<>();
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            T value = converter.apply(token);
            if (value == null) {
                invalid.add(token);
            } else {
                result.add(value);
            }
        }

        if (!invalid.isEmpty()) {
            errors.add(error(field, OrderFilterErrorCode.INVALID_FILTER_VALUE,
                    "Nilai filter " + label + " tidak valid: " + String.join(", ", invalid) + "."));
        }
        if (result.size() > maxValues) {
            errors.add(error(field, OrderFilterErrorCode.FILTER_TOO_MANY_VALUES,
                    "Filter " + label + " maksimal " + maxValues + " nilai."));
        }
        return result;
    }

    private static ErrorDto error(String field, String code, String message) {
        return new ErrorDto(field, message, code, null);
    }
}
