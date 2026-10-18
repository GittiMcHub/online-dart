package it.tobaben.dart.application.bot;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Production scheduler: one daemon thread "bot", lives as long as the process. */
public class ExecutorBotScheduler implements BotScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bot");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public Cancellable schedule(Duration delay, Runnable task) {
        ScheduledFuture<?> future = this.executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }
}
