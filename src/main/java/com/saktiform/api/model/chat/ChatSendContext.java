package com.saktiform.api.model.chat;

import java.util.UUID;

/**
 * Snapshot nilai yang dibutuhkan untuk mengirim pesan, dibaca di dalam transaksi
 * agar relasi LAZY (contact, waba) tidak diakses di luar transaksi.
 */
public record ChatSendContext(
        UUID conversationId,
        Long workspaceId,
        String deviceId,
        String phoneNumber,
        String contactName) {
}
