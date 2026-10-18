package it.tobaben.dart.game.bot;

/**
 * Difficulty of a bot: its 3-dart scoring average (0–180). From
 * SMART_FROM_AVERAGE on the bot also plays tactically (checkout routes,
 * avoiding Schnapszahlen and bogey numbers).
 */
public record BotProfile(int average) {

    public static final int SMART_FROM_AVERAGE = 40;

    public BotProfile {
        if (average < 0 || average > 180) {
            throw new IllegalArgumentException("Average muss zwischen 0 und 180 liegen: " + average);
        }
    }

    public double sigma() {
        return DifficultyCalibration.sigmaFor(this.average);
    }

    public boolean smart() {
        return this.average >= SMART_FROM_AVERAGE;
    }
}
