package it.tobaben.dart.game.cricket;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameEvent;
import it.tobaben.dart.game.GameSnapshot;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CricketGameTest {

    @Test
    void turnShouldRotateAfterThreeDarts() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        assertEquals(player1, game.getCurrentPlayer());
        game.playTurn(Segment.SINGLE_20);
        game.playTurn(Segment.SINGLE_20);
        assertEquals(player1, game.getCurrentPlayer());
        game.playTurn(Segment.SINGLE_1); // miss, still a dart
        assertEquals(player2, game.getCurrentPlayer());
        assertEquals(1, game.getDartSetHistory().size());
    }

    @Test
    void marksAndPointsShouldBeTracked() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_20);  // closes 20
        game.playTurn(Segment.SINGLE_20);  // 20 points
        game.playTurn(Segment.DOUBLE_20);  // 40 points
        assertEquals(60, game.getScoreboard().getPoints(player1));
        assertTrue(game.getScoreboard().isClosed(player1, 20));

        // player 2 closes 20 -> dead for everyone
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_1);
        game.playTurn(Segment.SINGLE_1);

        // player 1 hits dead 20 -> no more points
        game.playTurn(Segment.TRIPLE_20);
        assertEquals(60, game.getScoreboard().getPoints(player1));
    }

    @Test
    void gameShouldEndMidTurnWhenPlayerClosesAllWithEnoughPoints() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        // player 1 closes 15-20 in two turns, player 2 throws misses
        game.playTurn(Segment.TRIPLE_15);
        game.playTurn(Segment.TRIPLE_16);
        game.playTurn(Segment.TRIPLE_17);
        throwDartThreeTimes(game, Segment.SINGLE_1);   // player 2
        game.playTurn(Segment.TRIPLE_18);
        game.playTurn(Segment.TRIPLE_19);
        game.playTurn(Segment.TRIPLE_20);
        throwDartThreeTimes(game, Segment.SINGLE_1);   // player 2

        // bull: bullseye (2 marks), then bull (1 mark) closes all -> game over on 2nd dart
        game.playTurn(Segment.BULLS_EYE);
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.BULL));

        assertTrue(game.isOver());
        assertEquals(List.of(player1, player2), game.getRanking());
        assertTrue(game.getLastTurnEvents().contains(GameEvent.PLAYER_FINISHED));
        assertTrue(game.getLastTurnEvents().contains(GameEvent.GAME_OVER));
        // the winning turn was finished mid-turn
        List<it.tobaben.dart.game.board.DartSet> history = game.getDartSetHistory();
        assertTrue(history.get(history.size() - 1).isFinish());
        assertThrows(InvalidStateException.class, () -> game.playTurn(Segment.SINGLE_1));
    }

    @Test
    void closedAllButBehindOnPointsShouldNotEndGame() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        // player 1 closes 19 and banks 57 points
        game.playTurn(Segment.TRIPLE_19);
        game.playTurn(Segment.TRIPLE_19);
        game.playTurn(Segment.SINGLE_1);

        // player 2 closes everything but stays at 0 points -> no win
        game.playTurn(Segment.TRIPLE_15);
        game.playTurn(Segment.TRIPLE_16);
        game.playTurn(Segment.TRIPLE_17);
        throwDartThreeTimes(game, Segment.SINGLE_1);   // player 1 wastes
        game.playTurn(Segment.TRIPLE_18);
        game.playTurn(Segment.TRIPLE_19);
        game.playTurn(Segment.TRIPLE_20);
        throwDartThreeTimes(game, Segment.SINGLE_1);   // player 1 wastes
        game.playTurn(Segment.BULLS_EYE);
        game.playTurn(Segment.BULL); // player 2 closed all, 0 < 57 points
        assertFalse(game.isOver());
        assertTrue(game.getScoreboard().hasClosedAll(player2));
        assertTrue(game.getRanking().isEmpty());
    }

    @Test
    void shouldEmitEvents() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player = new Player("Player1");
        game.addPlayer(player);

        game.playTurn(Segment.SINGLE_20);
        assertEquals(List.of(GameEvent.GAME_STARTED, GameEvent.THROW), game.getLastTurnEvents());
        game.playTurn(Segment.SINGLE_20);
        assertEquals(List.of(GameEvent.THROW), game.getLastTurnEvents());
    }

    @Test
    void wallHitAndBoardHitShouldBeThrowsWithoutEffect() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.WALL_HIT);
        game.playTurn(Segment.BOARD_HIT);
        game.playTurn(Segment.WALL_HIT);
        // three darts used up, no marks, next player
        assertEquals(player2, game.getCurrentPlayer());
        assertEquals(0, game.getScoreboard().getPoints(player1));
    }

    @Test
    void snapshotShouldContainPointsAndMarks() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_20); // 20 points

        GameSnapshot snapshot = game.getSnapshot();
        assertEquals(CricketGame.GAME_MODE, snapshot.gameMode());
        assertEquals(player1, snapshot.currentPlayer());
        assertEquals(List.of(player1, player2), snapshot.playerOrder());
        assertEquals(20, snapshot.scores().get(player1));
        assertEquals(0, snapshot.scores().get(player2));
        assertEquals(1, snapshot.throwsLeftInTurn());
        assertEquals(20, snapshot.lastThrowScore());
        assertEquals(80, snapshot.turnScore());
        assertFalse(snapshot.over());
        assertEquals("3", snapshot.modeData().get("marks:Player1:20"));
        assertEquals("0", snapshot.modeData().get("marks:Player2:20"));
    }

    @Test
    void abortTurnShouldVoidMarksAndPoints() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_20); // 20 points
        game.abortTurn(); // bounce out mid-turn
        assertEquals(0, game.getScoreboard().getMarks(player1, 20));
        assertEquals(0, game.getScoreboard().getPoints(player1));
        assertEquals(player2, game.getCurrentPlayer());
    }

    @Test
    void endTurnShouldKeepMarksAndPassOn() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_20);
        game.endTurn();
        assertEquals(3, game.getScoreboard().getMarks(player1, 20));
        assertEquals(player2, game.getCurrentPlayer());
        assertEquals(1, game.getDartSetHistory().size());
    }

    @Test
    void undoLastTurnShouldRestoreMarksAndPointsIdempotently() throws GameOverException, InvalidStateException {
        CricketGame game = new CricketGame();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_20);
        game.playTurn(Segment.SINGLE_20); // turn done: closed 20 + 40 points

        game.undoLastTurn();
        assertEquals(0, game.getScoreboard().getMarks(player1, 20));
        assertEquals(0, game.getScoreboard().getPoints(player1));
        game.undoLastTurn();
        assertEquals(0, game.getScoreboard().getMarks(player1, 20));
        assertEquals(player2, game.getCurrentPlayer());
    }

    private static void throwDartThreeTimes(CricketGame game, Segment segment) throws InvalidStateException, GameOverException {
        game.playTurn(segment);
        game.playTurn(segment);
        game.playTurn(segment);
    }

}
