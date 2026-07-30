package com.saktiform.api.service.blast;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Konteks satu pengiriman, dibangun pada transaksi {@code beginSend} dan dipakai di luar transaksi
 * (saat memanggil WhatsApp API + sleep delay), lalu di-finalisasi di transaksi complete.
 */
@Getter
@Builder
public class SendContext {
    private final Long jobId;
    private final Long messageId;
    private final UUID campaignId;
    private final Long idWorkspace;
    private final String deviceId;
    private final String phone;
    private final String renderedMessage;
    private final String mediaUrl;   // URL publik untuk dikirim ke WA
    private final String mediaPath;  // path storage untuk disimpan di Chat.media (FASE 5)
    private final Long createdBy;    // pembuat campaign → assignee + label pengirim
    private final int attempt;
    private final int maxAttempts;
    private final int delayMs;
}
