package com.saktiform.api.util;

/**
 * Masking nomor telepon untuk log (PII, PRD §17): jangan log nomor lengkap.
 * Contoh: 628123456789 → 6281****789.
 */
public final class BlastPhoneMask {

    private BlastPhoneMask() {}

    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String p = phone.trim();
        if (p.length() <= 7) return "***";
        return p.substring(0, 4) + "****" + p.substring(p.length() - 3);
    }
}
