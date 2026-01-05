package com.saktiform.api.service.chat.bot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class BotDelayManager {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(10);

    private final Map<UUID, ScheduledFuture<?>> tasks =
            new ConcurrentHashMap<>();

    public void debounce(UUID chatId, Runnable task, long delaySeconds) {

        // 1️⃣ Batalkan task lama jika ada
        ScheduledFuture<?> previous = tasks.get(chatId);
        if (previous != null) {
            previous.cancel(false);
        }

        // 2️⃣ Jadwalkan task baru
        ScheduledFuture<?> future =
                scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);

        tasks.put(chatId, future);
    }

    public void clear(String chatId) {
        ScheduledFuture<?> task = tasks.remove(chatId);
        if (task != null) {
            task.cancel(false);
        }
    }
}
