package com.saktiform.api.service.label;

import java.util.regex.Pattern;

/** Validasi & normalisasi kode warna hex label. Hanya format #RRGGBB (6 digit) yang didukung. */
public final class HexColor {

    private static final Pattern P = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    private HexColor() {
    }

    /**
     * Validasi input sebagai #RRGGBB (prefix {@code #} opsional) dan kembalikan bentuk
     * ternormalisasi {@code #rrggbb} (lowercase, dengan prefix). Format lain (#RGB, #RRGGBBAA,
     * nama warna, dsb.) ditolak.
     *
     * @throws IllegalArgumentException bila format tidak valid
     */
    public static String normalize(String raw) {
        if (raw == null || !P.matcher(raw.trim()).matches()) {
            throw new IllegalArgumentException("colorHex harus format #RRGGBB (6 digit heksadesimal)");
        }
        String hex = raw.trim();
        if (!hex.startsWith("#")) {
            hex = "#" + hex;
        }
        return hex.toLowerCase();
    }
}
