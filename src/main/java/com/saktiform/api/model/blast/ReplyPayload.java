package com.saktiform.api.model.blast;

import java.time.Instant;
import java.util.UUID;

/**
 * Ringkasan Chat masuk (balasan customer) untuk disimpan sebagai first reply di blast_message (FR-13).
 */
public record ReplyPayload(
        UUID chatId,
        String message,
        String mediaType,
        String mediaPath,
        Instant createdAt
) {}
