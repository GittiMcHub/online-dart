package it.tobaben.dart.game.default301;

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



}