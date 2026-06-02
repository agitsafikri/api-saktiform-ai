package com.saktiform.api.service.chat.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class BotDelayManager {

    private static final Logger log =
            LoggerFactory.getLogger(BotDelayManager.class);

    private final ScheduledExecutorService scheduler;

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> tasks =
            new ConcurrentHashMap<>();

    public BotDelayManager() {
        int poolSize = Math.max(4,
                Runtime.getRuntime().availableProcessors() * 2);
        this.scheduler = Executors.newScheduledThreadPool(poolSize);
    }

    public void debounce(UUID conversationId, Runnable task, long delaySeconds) {

        tasks.compute(conversationId, (id, previous) -> {

            if (previous != null) {
                previous.cancel(false);
            }

            return scheduler.schedule(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Bot task error for conversationId={}", id, e);
                } finally {
                    tasks.remove(id);
                }
            }, delaySeconds, TimeUnit.SECONDS);

        });
    }

    public void clear(UUID conversationId) {
        ScheduledFuture<?> task = tasks.remove(conversationId);
        if (task != null) {
            task.cancel(false);
        }
    }
}
