package com.saktiform.api.util;

import java.util.regex.Pattern;

/**
 * Sanitasi teks bebas yang berasal dari input pengguna sebelum disimpan.
 *
 * <p>Dipakai pada {@code label}, {@code placeholder}, {@code helpText}, label/nilai
 * pilihan, serta nilai Custom Field bertipe TEXT/TEXTAREA yang datang dari endpoint
 * checkout publik.
 *
 * <p><b>Membuang tag</b>, bukan meng-escape — teks yang tersimpan harus berupa teks
 * polos. Escaping menghasilkan entitas HTML yang akan terbaca oleh pengguna pada
 * konteks non-HTML (mis. pesan WhatsApp, Excel).
 *
 * <p>Ini adalah lapisan pertama. Lapisan kedua — escaping saat render — tetap wajib
 * dilakukan frontend, karena data yang tersimpan sebelum sanitasi diberlakukan
 * tetap ada di basis data.
 */
public final class TextSanitizer {

    /** Blok script/style beserta isinya — dibuang seluruhnya, bukan hanya tag-nya. */
    private static final Pattern SCRIPT_STYLE_BLOCK = Pattern.compile(
            "(?is)<\\s*(script|style|iframe|object|embed)[^>]*>.*?<\\s*/\\s*\\1\\s*>");

    /** Sisa tag HTML apa pun. */
    private static final Pattern ANY_TAG = Pattern.compile("(?s)<[^>]*>");

    /** Karakter kendali selain tab, CR, dan LF. */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\t\r\n]]");

    private TextSanitizer() {
    }

    /**
     * Membuang tag HTML dan karakter kendali, lalu memangkas spasi di ujung.
     *
     * @return {@code null} bila input {@code null}
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String s = SCRIPT_STYLE_BLOCK.matcher(raw).replaceAll(" ");
        s = ANY_TAG.matcher(s).replaceAll(" ");
        s = CONTROL_CHARS.matcher(s).replaceAll("");
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        return s.trim();
    }

    /** Sanitasi lalu kembalikan {@code null} bila hasilnya kosong. */
    public static String sanitizeToNull(String raw) {
        String s = sanitize(raw);
        return (s == null || s.isEmpty()) ? null : s;
    }

    /**
     * Sanitasi untuk teks satu baris — seluruh baris baru dijadikan spasi.
     * Dipakai pada label, placeholder, dan nilai pilihan.
     */
    public static String sanitizeSingleLine(String raw) {
        String s = sanitize(raw);
        if (s == null) {
            return null;
        }
        return s.replaceAll("\\s*\\n\\s*", " ").replaceAll("\\s{2,}", " ").trim();
    }
}
