package it.tobaben.dart.game.default301;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class Game301Test {

    @Test
    void testSinglePlayerGame() throws GameOverException, InvalidStateException {
        Game301 game = new Game301();
        Player player = new Player("Player1");
        game.addPlayer(player);

        game.playTurn(Segment.TRIPLE_20);
        // not finished round should not be count
        assertEquals(0, game.getDartSetHistory().size());
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        // three darts thown, score should be updated
        assertEquals(121, game.getScoreboard().get(player));
        assertEquals(1, game.getDartSetHistory().size());

        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.BULLS_EYE);
        // rest 10
        assertEquals(10, game.getScoreboard().get(player));
        assertEquals(2, game.getDartSetHistory().size());

        // Test Bust after third throw
        game.playTurn(Segment.SINGLE_5);
        game.playTurn(Segment.SINGLE_4);
        game.playTurn(Segment.BULLS_EYE); // BUST
        assertEquals(10, game.getScoreboard().get(player));
        assertEquals(3, game.getDartSetHistory().size());
        assertTrue(game.getDartSetHistory().get(3-1).isBusted());

        // Test Bust after second throw
        game.playTurn(Segment.SINGLE_5);
        game.playTurn(Segment.BULLS_EYE); // BUST
        assertEquals(10, game.getScoreboard().get(player));
        assertEquals(4, game.getDartSetHistory().size());
        assertTrue(game.getDartSetHistory().get(4-1).isBusted());

        // Test Bust after first throw
        game.playTurn(Segment.BULLS_EYE); // BUST
        assertEquals(10, game.getScoreboard().get(player));
        assertEquals(5, game.getDartSetHistory().size());
        assertTrue(game.getDartSetHistory().get(5-1).isBusted());

        // Test Finish game
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.SINGLE_10));
        assertEquals(6, game.getDartSetHistory().size());
        assertFalse(game.getDartSetHistory().get(6-1).isBusted());
        assertTrue(game.getDartSetHistory().get(6-1).isDone());
        assertTrue(game.getDartSetHistory().get(6-1).isFinish());

    }

    @Test
    void tesTwoPlayerGame() throws GameOverException, InvalidStateException {
        Game301 game = new Game301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_10);
        game.playTurn(Segment.TRIPLE_10);
        game.playTurn(Segment.TRIPLE_10);
        // three darts thown, score should be updated
        assertEquals(211, game.getScoreboard().get(player1));
        assertEquals(301, game.getScoreboard().get(player2));

        // player 2s turn
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        assertEquals(211, game.getScoreboard().get(player1));
        assertEquals(121, game.getScoreboard().get(player2));

        // player 1s turn
        game.playTurn(Segment.TRIPLE_10);
        game.playTurn(Segment.TRIPLE_10);
        game.playTurn(Segment.TRIPLE_10);
        assertEquals(121, game.getScoreboard().get(player1));
        assertEquals(121, game.getScoreboard().get(player2));

        // player 2s turn
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20); // BUST
        assertEquals(121, game.getScoreboard().get(player1));
        assertEquals(121, game.getScoreboard().get(player2));

        // second round end
        assertTrue(game.getDartSetHistory().get(game.getDartSetHistory().size()-1).isBusted());

        // player 1s turn
        game.playTurn(Segment.TRIPLE_10);
        game.playTurn(Segment.TRIPLE_10);
        game.playTurn(Segment.TRIPLE_10);
        assertEquals(31, game.getScoreboard().get(player1));
        assertEquals(121, game.getScoreboard().get(player2));

        // Player 2 Out
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        //  Finish game
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.SINGLE_1));
        assertEquals(31, game.getScoreboard().get(player1));
        assertEquals(0, game.getScoreboard().get(player2));

        assertEquals(6, game.getDartSetHistory().size());
        assertFalse(game.getDartSetHistory().get(6-1).isBusted());
        assertTrue(game.getDartSetHistory().get(6-1).isDone());
        assertTrue(game.getDartSetHistory().get(6-1).isFinish());
    }

    @Test
    void testMultiPlayerGame() throws GameOverException, InvalidStateException {
        Game301 game = new Game301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        Player player3 = new Player("Player3");
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);

        // all player throw 180
        throwDartThreeTimes(game, Segment.TRIPLE_20);
        throwDartThreeTimes(game, Segment.TRIPLE_20);
        throwDartThreeTimes(game, Segment.TRIPLE_20);
        assertEquals(121, game.getScoreboard().get(player1));
        assertEquals(121, game.getScoreboard().get(player2));
        assertEquals(121, game.getScoreboard().get(player3));

        // all player throw 120
        throwDartThreeTimes(game, Segment.DOUBLE_20);
        throwDartThreeTimes(game, Segment.DOUBLE_20);
        throwDartThreeTimes(game, Segment.DOUBLE_20);
        assertEquals(1, game.getScoreboard().get(player1));
        assertEquals(1, game.getScoreboard().get(player2));
        assertEquals(1, game.getScoreboard().get(player3));

        // player 1 bust
        game.playTurn(Segment.BULL); // bust
        // player 2 finish
        game.playTurn(Segment.SINGLE_1);
        // player 3 bust
        game.playTurn(Segment.BULL); // bust
        //player 1 bust again
        game.playTurn(Segment.BULL); // bust
        // player 3 win
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.SINGLE_1));
        System.out.println(game.getRanking().stream().map(Player::getName).collect(Collectors.toList()));

    }

    private static void throwDartThreeTimes(Game301 game, Segment segment) throws InvalidStateException, GameOverException {
        game.playTurn(segment);
        game.playTurn(segment);
        game.playTurn(segment);
    }

}