package com.saktiform.api.service.blast.queue;

import java.util.List;

/**
 * Abstraksi antrian (NFR-8). Implementasi MVP = DB queue (PostgreSQL SKIP LOCKED).
 * Memungkinkan migrasi ke broker tanpa menyentuh business logic.
 */
public interface QueuePort {

    /** Klaim hingga {@code batchSize} job siap proses; mengembalikan id job yang berhasil diklaim. */
    List<Long> claim(int batchSize, String workerId, long leaseMs);
}
