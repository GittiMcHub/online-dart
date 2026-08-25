package it.tobaben.dart.application;

import it.tobaben.dart.game.board.Segment;
import lombok.Getter;

/**
 * Per-player statistics, field names and semantics identical to the anthrax
 * Statistik class (they end up 1:1 in the status/gameUpdate JSON): misses and
 * wall hits count as throws, a busting dart still counts into the sums, and
 * per-game reset only clears the game counters — everything else is
 * tournament-lifetime. Averages are 3-dart averages.
 */
@Getter
public class PlayerStatistics {

    public static final int MIN_WUERFE_UNSET = 999;

    private int anzWuerfeSpiel = 0;
    private int anzWuerfeTurnier = 0;
    private int summeSpiel = 0;
    private int summeTurnier = 0;
    private int anzMinWuerfeBisSpielende = MIN_WUERFE_UNSET;
    private int maxPunkteProSpielzug = 0;
    private float avgSpiel = 0.0f;
    private float avgTurnier = 0.0f;
    private int anzStrafpunkte = 0;
    private int anzRandTreffer = 0;
    private int anzWandTreffer = 0;
    private int anzBull = 0;
    private int anzBullseye = 0;
    private int anzEinerFeld = 0;
    private int anzDoubleFeld = 0;
    private int anzTripleFeld = 0;
    private int anzUeberworfen = 0;
    private int highestFinish = 0;
    // [multiplier-1][field 1-20 at index 0-19, bull at index 20]
    private final int[][] getroffeneFelder = new int[3][21];

    /** A dart that hit a scoring segment (including cricket numbers). */
    public void recordScoringThrow(Segment segment) {
        if (segment == Segment.BULL) {
            this.anzBull++;
        } else if (segment == Segment.BULLS_EYE) {
            this.anzBullseye++;
        } else {
            switch (segment.getMultiplier()) {
                case 1 -> this.anzEinerFeld++;
                case 2 -> this.anzDoubleFeld++;
                case 3 -> this.anzTripleFeld++;
            }
        }
        int field = segment.getScore() / segment.getMultiplier();
        this.getroffeneFelder[segment.getMultiplier() - 1][field > 20 ? 20 : field - 1]++;
        recordThrow(segment.getScore());
    }

    /** A dart that missed the board (998). */
    public void recordMiss() {
        this.anzRandTreffer++;
        recordThrow(0);
    }

    /** A dart that hit the wall (997). Penalty points are handled separately. */
    public void recordWallHit() {
        this.anzWandTreffer++;
        recordThrow(0);
    }

    private void recordThrow(int value) {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.summeSpiel += value;
        this.summeTurnier += value;
        this.avgSpiel = ((float) this.summeSpiel / this.anzWuerfeSpiel) * 3;
        this.avgTurnier = ((float) this.summeTurnier / this.anzWuerfeTurnier) * 3;
    }

    public void recordBust() {
        this.anzUeberworfen++;
    }

    public void addStrafpunkte(int anzahl) {
        this.anzStrafpunkte += anzahl;
    }

    /** Called at the end of every turn with the sum thrown in that turn. */
    public void recordTurnSum(int turnSum) {
        if (turnSum > this.maxPunkteProSpielzug) {
            this.maxPunkteProSpielzug = turnSum;
        }
    }

    /** Called when the player finishes a game. */
    public void recordFinish(int finishTurnSum) {
        if (this.anzWuerfeSpiel < this.anzMinWuerfeBisSpielende) {
            this.anzMinWuerfeBisSpielende = this.anzWuerfeSpiel;
        }
        if (finishTurnSum > this.highestFinish) {
            this.highestFinish = finishTurnSum;
        }
    }

    /** Reset before a new game of the tournament (same fields as anthrax resetSpieldaten). */
    public void resetGameData() {
        this.summeSpiel = 0;
        this.avgSpiel = 0.0f;
        this.anzWuerfeSpiel = 0;
    }
}
