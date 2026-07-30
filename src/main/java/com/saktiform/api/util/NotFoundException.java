package com.saktiform.api.util;

/**
 * Sumber daya tidak ditemukan, <b>atau</b> berada di luar workspace pemanggil.
 *
 * <p>Kedua kondisi sengaja menghasilkan exception dan pesan yang sama agar keberadaan
 * sumber daya milik tenant lain tidak terungkap (404, bukan 403).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
