package com.saktiform.api.service.blast.placeholder;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Sumber data untuk resolusi placeholder per-recipient. Extensible (future: order, product, tracking).
 */
@Getter
@AllArgsConstructor
public class BlastMessageContext {
    private final String recipientName;
    private final String recipientPhone;
}
