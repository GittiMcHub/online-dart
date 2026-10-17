package it.tobaben.dart.application.bot;

import java.time.Duration;

/** Delayed execution for bot actions; tests replace it with a manual scheduler. */
public interface BotScheduler {

    Cancellable schedule(Duration delay, Runnable task);

    @FunctionalInterface
    interface Cancellable {
        /** Cancels the task if it has not run yet. */
        void cancel();
    }
}
