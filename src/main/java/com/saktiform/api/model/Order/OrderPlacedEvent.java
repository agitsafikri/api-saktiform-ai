package com.saktiform.api.model.Order;

import java.time.Instant;
import java.util.UUID;

/**
 * Dipublikasikan sekali saat order baru benar-benar terbentuk dari checkout
 * (bukan order agen {@code CST_CHAT}, bukan abandoned, bukan update/konfirmasi).
 *
 * <p>Sengaja dipisah dari {@link OrderCreatedEvent}: event itu juga dipicu saat nomor
 * WhatsApp diubah dan saat konfirmasi order, sehingga tidak bisa dipakai untuk
 * menghitung order baru.
 */
public record OrderPlacedEvent(UUID orderId, Long workspaceId, Instant createdAt) {
}
