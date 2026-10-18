package it.tobaben.dart.game.bot;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameSnapshot;
import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CricketStrategyTest {

    static final Player BOT = new Player("Bot");
    static final Player ALICE = new Player("Alice");

    /** marks: "Bot:20" -> 3 etc.; unspecified numbers have 0 marks */
    static GameSnapshot cricket(int botPoints, int alicePoints, Map<String, Integer> marks) {
        Map<String, String> modeData = new HashMap<>();
        marks.forEach((key, value) -> modeData.put("marks:" + key, String.valueOf(value)));
        return new GameSnapshot("CRICKET", List.of(BOT, ALICE), BOT,
                Map.of(BOT, botPoints, ALICE, alicePoints), List.of(), false, 3, 0, 0, modeData);
    }

    @Test
    void naiveWorksDownTheNumbers() {
        CricketStrategy naive = new CricketStrategy(false);
        assertEquals(20, naive.targetNumber(cricket(0, 0, Map.of()), BOT));
        assertEquals(19, naive.targetNumber(cricket(0, 0, Map.of("Bot:20", 3)), BOT));
        Map<String, Integer> allButBull = Map.of("Bot:20", 3, "Bot:19", 3, "Bot:18", 3,
                "Bot:17", 3, "Bot:16", 3, "Bot:15", 3);
        assertEquals(25, naive.targetNumber(cricket(0, 0, allButBull), BOT));
    }

    @Test
    void smartScoresWhenBehind() {
        CricketStrategy smart = new CricketStrategy(true);
        // bot closed 20, Alice has 20 open, Alice leads -> score on 20
        assertEquals(20, smart.targetNumber(cricket(0, 40, Map.of("Bot:20", 3, "Alice:19", 3)), BOT));
    }

    @Test
    void smartBlocksNumbersTheOpponentHasClosed() {
        CricketStrategy smart = new CricketStrategy(true);
        // not behind; Alice closed 18 -> close 18 first to stop her points
        assertEquals(18, smart.targetNumber(cricket(0, 0, Map.of("Alice:18", 3)), BOT));
    }

    @Test
    void smartClosesHighestOpenNumberOtherwise() {
        CricketStrategy smart = new CricketStrategy(true);
        assertEquals(19, smart.targetNumber(cricket(0, 0, Map.of("Bot:20", 3)), BOT));
    }

    @Test
    void aimsAtTriplesAndTheCenterForBull() {
        CricketStrategy naive = new CricketStrategy(false);
        assertEquals(BoardGeometry.centerOf(Segment.TRIPLE_20), naive.aim(cricket(0, 0, Map.of()), BOT));
        Map<String, Integer> allButBull = Map.of("Bot:20", 3, "Bot:19", 3, "Bot:18", 3,
                "Bot:17", 3, "Bot:16", 3, "Bot:15", 3);
        assertEquals(AimPoint.CENTER, naive.aim(cricket(0, 0, allButBull), BOT));
    }
}
