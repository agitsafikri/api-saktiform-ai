package com.saktiform.api.service.blast.worker;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool worker Blast yang dikelola sendiri (BUKAN bean Spring TaskExecutor) agar tidak membajak
 * resolusi @Async global. Pola konsisten dengan {@code BotDelayManager}. Melacak in-flight untuk
 * backpressure (poller hanya mengklaim sebanyak kapasitas → batasi memori & risiko lease expiry).
 */
@Component
public class BlastWorkerExecutor {

    private static final Logger log = LoggerFactory.getLogger(BlastWorkerExecutor.class);

    private final ExecutorService pool;
    private final int maxInFlight;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    public BlastWorkerExecutor() {
        int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        this.maxInFlight = poolSize * 2;
        this.pool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "blast-worker-" + threadCounter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    public int availableCapacity() {
        return Math.max(0, maxInFlight - inFlight.get());
    }

    public void submit(Runnable task) {
        inFlight.incrementAndGet();
        pool.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                log.error("Blast worker task error", e);
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        // Graceful: hentikan penerimaan tugas baru; job in-flight diselesaikan; lease menjaga sisanya.
        pool.shutdown();
    }
}
