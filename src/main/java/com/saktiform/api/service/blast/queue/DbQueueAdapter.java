package com.saktiform.api.service.blast.queue;

import com.saktiform.api.repository.BlastJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementasi QueuePort berbasis PostgreSQL FOR UPDATE SKIP LOCKED.
 * Klaim dilakukan dalam satu transaksi singkat: SELECT ... FOR UPDATE SKIP LOCKED lalu UPDATE status=CLAIMED.
 */
@Component
public class DbQueueAdapter implements QueuePort {

    private final BlastJobRepository jobRepository;

    public DbQueueAdapter(BlastJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public List<Long> claim(int batchSize, String workerId, long leaseMs) {
        List<Long> ids = jobRepository.findClaimableIds(batchSize);
        if (ids.isEmpty()) {
            return ids;
        }
        jobRepository.claim(ids, workerId, leaseMs);
        return ids;
    }
}
