package it.tobaben.dart.game.cricket;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CricketScoreboardTest {

    Player player1;
    Player player2;
    CricketScoreboard board;

    @BeforeEach
    void setup() {
        this.player1 = new Player("1");
        this.player2 = new Player("2");
        this.board = new CricketScoreboard(List.of(player1, player2));
    }

    private void closeAll(Player player) {
        this.board.applyHit(player, Segment.TRIPLE_15);
        this.board.applyHit(player, Segment.TRIPLE_16);
        this.board.applyHit(player, Segment.TRIPLE_17);
        this.board.applyHit(player, Segment.TRIPLE_18);
        this.board.applyHit(player, Segment.TRIPLE_19);
        this.board.applyHit(player, Segment.TRIPLE_20);
        this.board.applyHit(player, Segment.BULLS_EYE);
        this.board.applyHit(player, Segment.BULL);
    }

    @Test
    void correctInitialization() {
        for (int number : CricketScoreboard.CRICKET_NUMBERS) {
            assertEquals(0, board.getMarks(player1, number));
        }
        assertEquals(0, board.getPoints(player1));
        assertFalse(board.isGameOver());
        assertTrue(board.getRanking().isEmpty());
    }

    @Test
    void marksShouldAccumulateAndClose() {
        assertEquals(0, board.applyHit(player1, Segment.SINGLE_20));
        assertEquals(1, board.getMarks(player1, 20));
        assertEquals(0, board.applyHit(player1, Segment.DOUBLE_20));
        assertEquals(3, board.getMarks(player1, 20));
        assertTrue(board.isClosed(player1, 20));
    }

    @Test
    void tripleShouldCloseImmediately() {
        board.applyHit(player1, Segment.TRIPLE_20);
        assertTrue(board.isClosed(player1, 20));
        assertEquals(0, board.getPoints(player1));
    }

    @Test
    void hitsOnOwnClosedNumberShouldScoreWhileOpponentIsOpen() {
        board.applyHit(player1, Segment.TRIPLE_20);
        assertEquals(20, board.applyHit(player1, Segment.SINGLE_20));
        assertEquals(60, board.applyHit(player1, Segment.TRIPLE_20));
        assertEquals(80, board.getPoints(player1));
    }

    @Test
    void closingThrowOverflowShouldScore() {
        board.applyHit(player1, Segment.SINGLE_20);
        board.applyHit(player1, Segment.SINGLE_20);
        // 2 marks + triple = 5 marks -> closes and scores 2 overflow marks
        assertEquals(40, board.applyHit(player1, Segment.TRIPLE_20));
        assertTrue(board.isClosed(player1, 20));
        assertEquals(40, board.getPoints(player1));
    }

    @Test
    void deadNumberShouldNotScore() {
        board.applyHit(player1, Segment.TRIPLE_20);
        board.applyHit(player2, Segment.TRIPLE_20);
        // both closed -> dead
        assertEquals(0, board.applyHit(player1, Segment.TRIPLE_20));
        assertEquals(0, board.getPoints(player1));
    }

    @Test
    void hitsBeforeClosingShouldNotScore() {
        assertEquals(0, board.applyHit(player1, Segment.SINGLE_20));
        assertEquals(0, board.applyHit(player1, Segment.SINGLE_20));
        assertEquals(0, board.getPoints(player1));
    }

    @Test
    void nonCricketSegmentsShouldDoNothing() {
        assertEquals(0, board.applyHit(player1, Segment.TRIPLE_14));
        assertEquals(0, board.applyHit(player1, Segment.SINGLE_1));
        assertEquals(0, board.applyHit(player1, Segment.WALL_HIT));
        assertEquals(0, board.applyHit(player1, Segment.BOARD_HIT));
        for (int number : CricketScoreboard.CRICKET_NUMBERS) {
            assertEquals(0, board.getMarks(player1, number));
        }
    }

    @Test
    void bullShouldCountSingleAndBullseyeDouble() {
        board.applyHit(player1, Segment.BULLS_EYE); // 2 marks
        assertEquals(2, board.getMarks(player1, 25));
        board.applyHit(player1, Segment.BULL);      // 3rd mark closes
        assertTrue(board.isClosed(player1, 25));
        // bullseye on closed bull = 2 overflow marks = 50 points
        assertEquals(50, board.applyHit(player1, Segment.BULLS_EYE));
    }

    @Test
    void closedAllWithMostPointsShouldWin() {
        // player 1 closes 20 and scores
        board.applyHit(player1, Segment.TRIPLE_20);
        board.applyHit(player1, Segment.TRIPLE_20); // 60 points
        assertFalse(board.isGameOver());
        closeAll(player1);
        assertTrue(board.hasClosedAll(player1));
        assertTrue(board.isGameOver());
        assertEquals(List.of(player1, player2), board.getRanking());
    }

    @Test
    void closedAllWithFewerPointsShouldNotWin() {
        // player 2 leads on points
        board.applyHit(player2, Segment.TRIPLE_19);
        board.applyHit(player2, Segment.TRIPLE_19); // 57 points
        closeAll(player1);
        assertTrue(board.hasClosedAll(player1));
        assertFalse(board.isGameOver());
    }

    @Test
    void closedAllWithEqualPointsShouldWin() {
        // tie on points (0:0): closing all is enough
        closeAll(player1);
        assertTrue(board.isGameOver());
        assertEquals(player1, board.getRanking().get(0));
    }

    @Test
    void singlePlayerShouldWinByClosingAll() {
        this.board = new CricketScoreboard(List.of(player1));
        closeAll(player1);
        assertTrue(board.isGameOver());
        assertEquals(List.of(player1), board.getRanking());
    }

    @Test
    void rankingShouldOrderLosersByPoints() {
        Player player3 = new Player("3");
        this.board = new CricketScoreboard(List.of(player1, player2, player3));
        // player 3 scores 57 points, player 2 none
        board.applyHit(player3, Segment.TRIPLE_19);
        board.applyHit(player3, Segment.TRIPLE_19);
        // player 1 needs at least as many points to win
        board.applyHit(player1, Segment.TRIPLE_20);
        board.applyHit(player1, Segment.TRIPLE_20); // 60 points
        closeAll(player1);
        assertTrue(board.isGameOver());
        assertEquals(List.of(player1, player3, player2), board.getRanking());
    }

}
