package com.saktiform.api.model.Order;

import java.util.UUID;

/**
 * Payload WebSocket {@code ORDER_CREATED}. Sengaja minimal (tanpa data pelanggan):
 * frontend hanya menaikkan penghitung "order baru" lalu memuat ulang list via REST.
 *
 * @param createdAt ISO-8601 UTC, mis. {@code 2026-11-23T03:15:00Z}
 */
public record OrderCreatedNotification(UUID orderId, String createdAt) {
}
