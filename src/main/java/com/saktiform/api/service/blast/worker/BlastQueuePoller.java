package com.saktiform.api.service.blast.worker;

import com.saktiform.api.service.blast.BlastSenderService;
import com.saktiform.api.service.blast.queue.QueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;

/**
 * Poller terjadwal: tiap node mengklaim batch job READY (SKIP LOCKED via QueuePort) dan menyerahkannya
 * ke executor. Mengklaim hanya sebanyak kapasitas executor (backpressure). Multi-node aman: klaim disjoint.
 */
@Component
public class BlastQueuePoller {

    private static final Logger log = LoggerFactory.getLogger(BlastQueuePoller.class);

    private final QueuePort queuePort;
    private final BlastWorkerExecutor executor;
    private final BlastSenderService senderService;

    @Value("${blast.worker.batch-size:100}")
    private int batchSize;

    @Value("${blast.worker.lease-duration-ms:60000}")
    private long leaseMs;

    private final String workerId = buildWorkerId();

    public BlastQueuePoller(QueuePort queuePort, BlastWorkerExecutor executor, BlastSenderService senderService) {
        this.queuePort = queuePort;
        this.executor = executor;
        this.senderService = senderService;
    }

    @Scheduled(fixedDelayString = "${blast.worker.poll-interval-ms:1000}")
    public void poll() {
        try {
            int capacity = executor.availableCapacity();
            if (capacity <= 0) {
                return; // executor penuh; tunggu siklus berikutnya
            }
            int toClaim = Math.min(batchSize, capacity);
            List<Long> ids = queuePort.claim(toClaim, workerId, leaseMs);
            for (Long jobId : ids) {
                executor.submit(() -> senderService.processJob(jobId));
            }
        } catch (Exception e) {
            log.error("Blast poller error", e);
        }
    }

    private static String buildWorkerId() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + suffix;
        } catch (Exception e) {
            return "worker-" + suffix;
        }
    }
}
