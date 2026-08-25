package it.tobaben.dart.game.x01;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.exceptions.BustException;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import it.tobaben.dart.game.exceptions.PlayerFinishedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CountingScoreboardTest {

    Player player1;
    Player player2;
    CountingScoreboard board;
    @BeforeEach
    void setup(){
        this.player1 = new Player("1");
        this.player2 = new Player("2");
        this.board = new CountingScoreboard(
                Set.of(player1, player2),
                301,
                0
                );
    }

    @Test
    void correctInitialization(){
        assertEquals(301, board.getScore(player1));
        assertEquals(301, board.getScore(player2));
        assertTrue(board.getRanking().isEmpty());
    }

    @Test
    void noChangeWithoutCommit() throws BustException {
        assertThrows(InvalidStateException.class, () -> {
            this.board.changeScore(50);
        });
        assertEquals(301, board.getScore(player1));
        assertEquals(301, board.getScore(player2));
    }

    @Test
    void shouldCountCorrectly() throws BustException, GameOverException, InvalidStateException, PlayerFinishedException {
        this.board.startCount(player1);
        this.board.changeScore(50);
        this.board.changeScore(50);
        this.board.changeScore(1);
        this.board.commit();
        assertEquals(200, board.getScore(player1));
        assertEquals(301, board.getScore(player2));
    }

    @Test
    void shouldBustCorrectly() throws BustException, GameOverException, InvalidStateException, PlayerFinishedException {
        this.board = new CountingScoreboard(
                Set.of(player1, player2),
                100,
                0
        );
        this.board.startCount(player1);
        this.board.changeScore(80);
        assertThrows(BustException.class, () -> this.board.changeScore(80));
    }

    @Test
    void scoreboardShouldStopAtTwoPlayersWhenOneIsDone(){
        // When two players play, and one finishes, the game should be over
        this.board.startCount(player1);
        assertThrows(PlayerFinishedException.class, () -> this.board.changeScore(301));
        assertThrows(GameOverException.class, () -> this.board.commit());

        assertEquals(player1, this.board.getRanking().get(0));
        assertEquals(player2, this.board.getRanking().get(1));
    }

    @Test
    void scoreboardShouldWorkForSinglePlayer(){
        this.board = new CountingScoreboard(
                Set.of(player1),
                100,
                0
        );
        this.board.startCount(player1);
        assertThrows(PlayerFinishedException.class, () -> this.board.changeScore(100));
        assertThrows(GameOverException.class, () -> this.board.commit());

        assertEquals(player1, this.board.getRanking().get(0));
    }

    @Test
    void shouldAddUpCorrectly() throws BustException, GameOverException, InvalidStateException, PlayerFinishedException {
        this.board = new CountingScoreboard(
                Set.of(player1, player2),
                0,
                100
        );
        this.board.startCount(player1);
        this.board.changeScore(60);
        this.board.commit();
        assertEquals(60, board.getScore(player1));
        assertEquals(0, board.getScore(player2));
    }

    @Test
    void shouldBustInAddUpModeWhenOvershootingTarget() throws BustException, InvalidStateException, PlayerFinishedException {
        this.board = new CountingScoreboard(
                Set.of(player1, player2),
                0,
                100
        );
        this.board.startCount(player1);
        this.board.changeScore(60);
        assertThrows(BustException.class, () -> this.board.changeScore(60));
    }

    @Test
    void shouldFinishPlayerInAddUpModeAtTargetScore(){
        this.board = new CountingScoreboard(
                Set.of(player1, player2),
                0,
                100
        );
        this.board.startCount(player1);
        assertThrows(PlayerFinishedException.class, () -> this.board.changeScore(100));
        assertThrows(GameOverException.class, () -> this.board.commit());
        assertEquals(player1, this.board.getRanking().get(0));
        assertEquals(player2, this.board.getRanking().get(1));
    }

    @Test
    void gameShouldNotBeOverWhenFirstOfThreePlayersFinishes() throws BustException, GameOverException, InvalidStateException {
        // regression for the old isGameOver bug: with more than two players the first
        // finished player must NOT end the game
        Player player3 = new Player("3");
        this.board = new CountingScoreboard(
                Set.of(player1, player2, player3),
                301,
                0
        );
        this.board.startCount(player1);
        assertThrows(PlayerFinishedException.class, () -> this.board.changeScore(301));
        assertDoesNotThrow(() -> this.board.commit());
        assertEquals(1, this.board.getRanking().size());

        // second player finishes -> only one left -> game over, last place is filled up
        this.board.startCount(player2);
        assertThrows(PlayerFinishedException.class, () -> this.board.changeScore(301));
        assertThrows(GameOverException.class, () -> this.board.commit());
        assertEquals(player1, this.board.getRanking().get(0));
        assertEquals(player2, this.board.getRanking().get(1));
        assertEquals(player3, this.board.getRanking().get(2));
    }

    @Test
    void commitShouldClearStateBeforeThrowingGameOver(){
        this.board.startCount(player1);
        assertThrows(PlayerFinishedException.class, () -> this.board.changeScore(301));
        assertThrows(GameOverException.class, () -> this.board.commit());
        // regression: the open count must be cleared even when GameOverException is thrown
        assertThrows(InvalidStateException.class, () -> this.board.changeScore(1));
        assertThrows(InvalidStateException.class, () -> this.board.getCurrentScore());
    }

    @Test
    void nonFinishingThrowOnTargetScoreShouldBust() throws BustException, InvalidStateException, PlayerFinishedException {
        this.board = new CountingScoreboard(
                Set.of(player1, player2),
                40,
                0
        );
        this.board.startCount(player1);
        // reaches exactly 0, but the throw is not allowed to finish (e.g. double-out required)
        assertThrows(BustException.class, () -> this.board.changeScore(40, false));
        assertTrue(this.board.getRanking().isEmpty());
    }

    @Test
    void getCurrentScoreShouldReturnStagedScore() throws BustException, InvalidStateException, PlayerFinishedException {
        this.board.startCount(player1);
        this.board.changeScore(60);
        assertEquals(241, this.board.getCurrentScore());
        // staged only, not persisted yet
        assertEquals(301, this.board.getScore(player1));
    }

}
