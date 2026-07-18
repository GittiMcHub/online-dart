package it.tobaben.dart.game.x01;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameEvent;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class X01GameTest {

    private static X01Game default301(){
        return new X01Game(301, false, false);
    }

    @Test
    void testSinglePlayerGame() throws GameOverException, InvalidStateException {
        X01Game game = default301();
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
        assertTrue(game.isOver());
    }

    @Test
    void tesTwoPlayerGame() throws GameOverException, InvalidStateException {
        X01Game game = default301();
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
        assertTrue(game.isOver());
        // winner first, loser fills the last place
        assertEquals(player2, game.getRanking().get(0));
        assertEquals(player1, game.getRanking().get(1));
    }

    @Test
    void testMultiPlayerGame() throws GameOverException, InvalidStateException {
        X01Game game = default301();
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
        // player 2 finished first, player 3 second, player 1 fills the last place
        assertEquals(player2, game.getRanking().get(0));
        assertEquals(player3, game.getRanking().get(1));
        assertEquals(player1, game.getRanking().get(2));
    }

    @Test
    void gameShouldContinueWhenFirstOfThreePlayersFinishes() throws GameOverException, InvalidStateException {
        // regression for the old isGameOver bug: first finished player must not end the game
        X01Game game = new X01Game(50, false, false);
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        Player player3 = new Player("Player3");
        game.addPlayer(player1);
        game.addPlayer(player2);
        game.addPlayer(player3);

        game.playTurn(Segment.BULLS_EYE); // player 1 finishes
        assertFalse(game.isOver());
        assertEquals(player2, game.getCurrentPlayer());

        // player 1 is skipped from now on
        throwDartThreeTimes(game, Segment.SINGLE_1);   // player 2
        throwDartThreeTimes(game, Segment.SINGLE_1);   // player 3
        assertEquals(player2, game.getCurrentPlayer());
    }

    @Test
    void test501Game() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(501, false, false);
        Player player = new Player("Player1");
        game.addPlayer(player);

        throwDartThreeTimes(game, Segment.TRIPLE_20);
        assertEquals(321, game.getScoreboard().get(player));
    }

    @Test
    void snapshotShouldShowStagedScoreDuringTurn() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player = new Player("Player1");
        game.addPlayer(player);

        // mid-turn: displays must see the score per throw, not only after commit
        game.playTurn(Segment.TRIPLE_20);
        assertEquals(241, game.getSnapshot().scores().get(player));
        game.playTurn(Segment.SINGLE_20);
        assertEquals(221, game.getSnapshot().scores().get(player));
        game.playTurn(Segment.SINGLE_1);
        assertEquals(220, game.getSnapshot().scores().get(player));
    }

    @Test
    void snapshotShouldRestoreCommittedScoreAfterBust() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(40, false, false);
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.SINGLE_20);
        assertEquals(20, game.getSnapshot().scores().get(player1));
        game.playTurn(Segment.TRIPLE_20); // bust: turn is voided
        assertEquals(40, game.getSnapshot().scores().get(player1));
    }

    @Test
    void currentPlayerShouldBeSetBeforeAndDuringGame() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);
        // regression: getCurrentPlayer used to return null
        assertEquals(player1, game.getCurrentPlayer());
        throwDartThreeTimes(game, Segment.SINGLE_1);
        assertEquals(player2, game.getCurrentPlayer());
    }

    @Test
    void doubleInShouldIgnoreScoreUntilFirstDouble() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(301, true, false);
        Player player = new Player("Player1");
        game.addPlayer(player);

        // not opened yet: darts are thrown but do not score
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_20);
        game.playTurn(Segment.DOUBLE_10); // opens, counts
        assertEquals(281, game.getScoreboard().get(player));

        // opened from now on
        throwDartThreeTimes(game, Segment.SINGLE_20);
        assertEquals(221, game.getScoreboard().get(player));
    }

    @Test
    void doubleOutShouldRequireDoubleToFinish() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(40, false, true);
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        // player 1 reaches exactly 0 with a single -> bust
        game.playTurn(Segment.SINGLE_20);
        game.playTurn(Segment.SINGLE_20); // 0 reached, but no double -> BUST
        assertTrue(game.getDartSetHistory().get(0).isBusted());
        assertEquals(40, game.getScoreboard().get(player1));

        // player 2 finishes with double 20 -> game over
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.DOUBLE_20));
        assertTrue(game.isOver());
        assertEquals(player2, game.getRanking().get(0));
    }

    @Test
    void doubleOutShouldBustWhenRemainingScoreWouldBeOne() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(40, false, true);
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        // 40 - 39 = 1 -> impossible to finish with a double -> bust
        game.playTurn(Segment.TRIPLE_13);
        assertTrue(game.getDartSetHistory().get(0).isBusted());
        assertEquals(40, game.getScoreboard().get(player1));
        assertEquals(player2, game.getCurrentPlayer());
    }

    @Test
    void bullsEyeShouldFinishDoubleOutGame() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(50, false, true);
        Player player = new Player("Player1");
        game.addPlayer(player);

        // bullseye counts as double bull and may finish
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.BULLS_EYE));
        assertTrue(game.isOver());
    }

    @Test
    void shouldEmitEvents() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player = new Player("Player1");
        game.addPlayer(player);

        game.playTurn(Segment.TRIPLE_20);
        assertEquals(java.util.List.of(GameEvent.GAME_STARTED, GameEvent.THROW), game.getLastTurnEvents());

        game.playTurn(Segment.TRIPLE_20);
        assertEquals(java.util.List.of(GameEvent.THROW), game.getLastTurnEvents());

        // 180 in one turn
        game.playTurn(Segment.TRIPLE_20);
        assertTrue(game.getLastTurnEvents().contains(GameEvent.MAX_POINTS));

        // bust: 121 remaining, 3x TRIPLE_20 overshoots on the third dart
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_2); // 121 - 122 -> bust
        assertTrue(game.getLastTurnEvents().contains(GameEvent.BUST));

        // finish: 121 remaining
        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        assertThrows(GameOverException.class, () -> game.playTurn(Segment.SINGLE_1));
        assertTrue(game.getLastTurnEvents().contains(GameEvent.PLAYER_FINISHED));
        assertTrue(game.getLastTurnEvents().contains(GameEvent.GAME_OVER));
    }

    @Test
    void playTurnAfterGameOverShouldFail(){
        X01Game game = new X01Game(50, false, false);
        Player player = new Player("Player1");
        game.addPlayer(player);

        assertThrows(GameOverException.class, () -> game.playTurn(Segment.BULLS_EYE));
        assertTrue(game.isOver());
        assertThrows(InvalidStateException.class, () -> game.playTurn(Segment.SINGLE_1));
    }

    @Test
    void wallHitAndBoardHitShouldScoreZero() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player = new Player("Player1");
        game.addPlayer(player);

        game.playTurn(Segment.WALL_HIT);
        game.playTurn(Segment.BOARD_HIT);
        game.playTurn(Segment.SINGLE_20);
        assertEquals(281, game.getScoreboard().get(player));
    }

    @Test
    void snapshotShouldContainGameState() throws GameOverException, InvalidStateException {
        X01Game game = new X01Game(301, false, true);
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        // before the first throw
        it.tobaben.dart.game.GameSnapshot snapshot = game.getSnapshot();
        assertEquals(X01Game.GAME_MODE, snapshot.gameMode());
        assertEquals(301, snapshot.scores().get(player1));
        assertEquals(3, snapshot.throwsLeftInTurn());
        assertEquals(0, snapshot.lastThrowScore());

        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.SINGLE_5);

        snapshot = game.getSnapshot();
        assertEquals(player1, snapshot.currentPlayer());
        assertEquals(List.of(player1, player2), snapshot.playerOrder());
        assertEquals(236, snapshot.scores().get(player1)); // staged mid-turn score
        assertEquals(1, snapshot.throwsLeftInTurn());
        assertEquals(5, snapshot.lastThrowScore());
        assertEquals(65, snapshot.turnScore());
        assertFalse(snapshot.over());
        assertEquals("301", snapshot.modeData().get("startScore"));
        assertEquals("false", snapshot.modeData().get("doubleIn"));
        assertEquals("true", snapshot.modeData().get("doubleOut"));

        game.playTurn(Segment.SINGLE_5);
        snapshot = game.getSnapshot();
        assertEquals(231, snapshot.scores().get(player1)); // turn committed
        assertEquals(player2, snapshot.currentPlayer());
        assertEquals(3, snapshot.throwsLeftInTurn());
    }

    @Test
    void endTurnShouldKeepThrownDartsAndPassOn() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.SINGLE_20);
        game.endTurn(); // NEXT after one dart
        assertEquals(281, game.getScoreboard().get(player1));
        assertEquals(player2, game.getCurrentPlayer());
        assertTrue(game.getLastTurnEvents().contains(GameEvent.TURN_ENDED));
        assertEquals(1, game.getDartSetHistory().size());
    }

    @Test
    void endTurnWithoutDartsShouldJustPassOn() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.endTurn(); // NEXT before any dart, even before the game started
        assertEquals(player2, game.getCurrentPlayer());
        assertEquals(301, game.getScoreboard().get(player1));
        assertEquals(0, game.getDartSetHistory().size());
    }

    @Test
    void abortTurnShouldVoidOpenTurn() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        game.playTurn(Segment.TRIPLE_20);
        game.playTurn(Segment.TRIPLE_20);
        game.abortTurn(); // bounce out mid-turn
        assertEquals(301, game.getScoreboard().get(player1));
        assertEquals(player2, game.getCurrentPlayer());
        assertTrue(game.getLastTurnEvents().contains(GameEvent.TURN_ENDED));
    }

    @Test
    void undoLastTurnShouldRestoreCommittedScoreIdempotently() throws GameOverException, InvalidStateException {
        X01Game game = default301();
        Player player1 = new Player("Player1");
        Player player2 = new Player("Player2");
        game.addPlayer(player1);
        game.addPlayer(player2);

        throwDartThreeTimes(game, Segment.TRIPLE_20);
        assertEquals(121, game.getScoreboard().get(player1));

        game.undoLastTurn();
        assertEquals(301, game.getScoreboard().get(player1));
        // idempotent: reporting the bounce out twice must not restore an older turn
        game.undoLastTurn();
        assertEquals(301, game.getScoreboard().get(player1));
        // play order is unchanged, player 2 is next
        assertEquals(player2, game.getCurrentPlayer());
    }

    @Test
    void undoLastTurnBeforeAnyTurnShouldDoNothing() {
        X01Game game = default301();
        Player player = new Player("Player1");
        game.addPlayer(player);
        assertDoesNotThrow(game::undoLastTurn);
    }

    private static void throwDartThreeTimes(X01Game game, Segment segment) throws InvalidStateException, GameOverException {
        game.playTurn(segment);
        game.playTurn(segment);
        game.playTurn(segment);
    }

}
