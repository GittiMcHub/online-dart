package it.tobaben.dart.application.bot;

import java.time.Duration;

/**
 * Human-like pacing of a bot: a dart every minDart–maxDart (uniform), the
 * darts are pulled (999) nextDelay after the turn, a game the bot finished is
 * completed after gameEndDelay; the watchdog resends an action that got no
 * reaction.
 */
public record BotTiming(Duration minDart, Duration maxDart, Duration nextDelay,
                        Duration gameEndDelay, Duration watchdog) {

    public static final BotTiming DEFAULT = new BotTiming(Duration.ofMillis(1500), Duration.ofMillis(3000),
            Duration.ofMillis(2500), Duration.ofSeconds(5), Duration.ofSeconds(10));

    /** For tests: no waiting, watchdog stays at 10 s. */
    public static final BotTiming INSTANT = new BotTiming(Duration.ZERO, Duration.ZERO,
            Duration.ZERO, Duration.ZERO, Duration.ofSeconds(10));
}
