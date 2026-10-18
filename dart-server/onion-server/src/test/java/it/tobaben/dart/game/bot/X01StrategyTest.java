package it.tobaben.dart.game.bot;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameSnapshot;
import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class X01StrategyTest {

    static final Player BOT = new Player("Bot");
    static final IntPredicate NO_PENALTY = rest -> false;
    /** Schnapszahl as in HouseRules.isSchnapszahl: at least two digits, all equal */
    static final IntPredicate SCHNAPSZAHL = rest -> rest >= 10
            && String.valueOf(rest).chars().distinct().count() == 1;

    static GameSnapshot x01(int rest, int dartsLeft, boolean doubleIn, boolean doubleOut, int startScore) {
        return new GameSnapshot("X01", List.of(BOT), BOT, Map.of(BOT, rest), List.of(), false,
                dartsLeft, 0, 0,
                Map.of("startScore", String.valueOf(startScore),
                        "doubleIn", String.valueOf(doubleIn),
                        "doubleOut", String.valueOf(doubleOut)));
    }

    static Segment smart(int rest, int dartsLeft, boolean doubleOut) {
        return smartWith(NO_PENALTY, rest, dartsLeft, doubleOut);
    }

    static Segment smartWith(IntPredicate penalized, int rest, int dartsLeft, boolean doubleOut) {
        X01Strategy strategy = new X01Strategy(true, new ThrowModel(16, new Random(1)), penalized);
        return strategy.target(x01(rest, dartsLeft, false, doubleOut, 501), BOT);
    }

    static Segment naive(int rest, boolean doubleOut) {
        X01Strategy strategy = new X01Strategy(false, new ThrowModel(85, new Random(1)), NO_PENALTY);
        return strategy.target(x01(rest, 3, false, doubleOut, 501), BOT);
    }

    @Test
    void doubleInPendingAimsAtDoubleTwenty() {
        X01Strategy strategy = new X01Strategy(true, new ThrowModel(16, new Random(1)), NO_PENALTY);
        assertEquals(Segment.DOUBLE_20, strategy.target(x01(301, 3, true, true, 301), BOT));
        X01Strategy naive = new X01Strategy(false, new ThrowModel(85, new Random(1)), NO_PENALTY);
        assertEquals(Segment.DOUBLE_20, naive.target(x01(301, 3, true, false, 301), BOT));
        // once in, normal scoring
        assertEquals(Segment.TRIPLE_20, strategy.target(x01(261, 3, true, true, 301), BOT));
    }

    @Test
    void smartDoubleOutFollowsTheCheckoutTable() {
        assertEquals(Segment.TRIPLE_20, smart(301, 3, true));
        assertEquals(Checkouts.route(81, 3).orElseThrow().get(0), smart(81, 3, true));
        assertEquals(Segment.TRIPLE_20, smart(170, 3, true));
        assertEquals(Segment.DOUBLE_20, smart(40, 2, true));
        assertEquals(Segment.SINGLE_10, smart(50, 3, true));
        assertEquals(Segment.BULLS_EYE, smart(50, 1, true));
    }

    @Test
    void smartDoubleOutSetsUpWhenNoRouteFits() {
        assertEquals(Segment.SINGLE_1, smart(3, 1, true));    // leaves 2
        assertEquals(Segment.TRIPLE_7, smart(61, 1, true));   // leaves 40
        assertEquals(Segment.TRIPLE_20, smart(200, 1, true)); // too far: score
    }

    @Test
    void straightOutAimsAtTheExactSegment() {
        assertEquals(Segment.TRIPLE_15, smart(45, 1, false));
        assertEquals(Segment.BULL, smart(25, 1, false));
        assertEquals(Segment.SINGLE_20, smart(59, 1, false));
        assertEquals(Segment.TRIPLE_20, smart(121, 3, false));
        assertEquals(Segment.SINGLE_18, naive(18, false));
    }

    @Test
    void naiveDoubleOut() {
        assertEquals(Segment.TRIPLE_20, naive(200, true));
        assertEquals(Segment.DOUBLE_16, naive(32, true));
        assertEquals(Segment.SINGLE_5, naive(45, true));
        assertEquals(Segment.SINGLE_1, naive(33, true));
        assertEquals(Segment.BULLS_EYE, naive(50, true));
    }

    @Test
    void lastDartAvoidsLeavingASchnapszahl() {
        // 171 - T20 = 111
        assertNotEquals(Segment.TRIPLE_20, smartWith(SCHNAPSZAHL, 171, 1, true));
        // rule off: bogey risk alone stays below the threshold, T20 it is
        assertEquals(Segment.TRIPLE_20, smartWith(NO_PENALTY, 171, 1, true));
        // not the last dart: no filter
        assertEquals(Segment.TRIPLE_20, smartWith(SCHNAPSZAHL, 171, 3, true));
    }

    @Test
    void penaltyFilterNeverPicksADeliberateBust() {
        // rest 33, last dart: every alternative T20/T19/... busts on a direct hit and must be skipped
        Segment target = smartWith(rest -> true, 33, 1, true);
        assertTrue(33 - target.getScore() >= 2 || 33 - target.getScore() == 0, "target " + target);
    }

    @Test
    void aimReturnsTheCenterOfTheTarget() {
        X01Strategy strategy = new X01Strategy(true, new ThrowModel(16, new Random(1)), NO_PENALTY);
        assertEquals(BoardGeometry.centerOf(Segment.TRIPLE_20), strategy.aim(x01(301, 3, false, true, 301), BOT));
    }
}
