package com.saktiform.api.service.blast.placeholder;

/**
 * Resolver satu placeholder {{key}}. Menambah placeholder baru = menambah 1 implementasi (Open/Closed).
 */
public interface PlaceholderResolver {
    String key();

    String resolve(BlastMessageContext ctx);
}
