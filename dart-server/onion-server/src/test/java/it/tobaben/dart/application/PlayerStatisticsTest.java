package it.tobaben.dart.application;

import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatisticsTest {

    @Test
    void scoringThrowShouldUpdateCountersAndAverages() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.recordScoringThrow(Segment.TRIPLE_20);
        assertEquals(1, statistics.getAnzWuerfeSpiel());
        assertEquals(1, statistics.getAnzWuerfeTurnier());
        assertEquals(60, statistics.getSummeSpiel());
        assertEquals(1, statistics.getAnzTripleFeld());
        assertEquals(180.0f, statistics.getAvgSpiel()); // 3-dart average
        assertEquals(1, statistics.getGetroffeneFelder()[2][19]); // triple 20

        statistics.recordScoringThrow(Segment.SINGLE_20);
        assertEquals(120.0f, statistics.getAvgSpiel()); // (80/2)*3
        assertEquals(1, statistics.getAnzEinerFeld());
    }

    @Test
    void bullAndBullseyeAreCountedSeparately() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.recordScoringThrow(Segment.BULL);
        statistics.recordScoringThrow(Segment.BULLS_EYE);
        assertEquals(1, statistics.getAnzBull());
        assertEquals(1, statistics.getAnzBullseye());
        assertEquals(0, statistics.getAnzEinerFeld());
        assertEquals(0, statistics.getAnzDoubleFeld());
        assertEquals(1, statistics.getGetroffeneFelder()[0][20]);
        assertEquals(1, statistics.getGetroffeneFelder()[1][20]);
    }

    @Test
    void missesAndWallHitsCountAsThrows() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.recordMiss();
        statistics.recordWallHit();
        assertEquals(2, statistics.getAnzWuerfeSpiel());
        assertEquals(1, statistics.getAnzRandTreffer());
        assertEquals(1, statistics.getAnzWandTreffer());
        assertEquals(0.0f, statistics.getAvgSpiel());
    }

    @Test
    void finishShouldTrackMinThrowsAndHighestFinish() {
        PlayerStatistics statistics = new PlayerStatistics();
        assertEquals(PlayerStatistics.MIN_WUERFE_UNSET, statistics.getAnzMinWuerfeBisSpielende());
        statistics.recordScoringThrow(Segment.TRIPLE_20);
        statistics.recordScoringThrow(Segment.TRIPLE_20);
        statistics.recordFinish(120);
        assertEquals(2, statistics.getAnzMinWuerfeBisSpielende());
        assertEquals(120, statistics.getHighestFinish());
        // a worse (higher) throw count or lower finish must not overwrite
        statistics.recordScoringThrow(Segment.SINGLE_1);
        statistics.recordFinish(50);
        assertEquals(2, statistics.getAnzMinWuerfeBisSpielende());
        assertEquals(120, statistics.getHighestFinish());
    }

    @Test
    void turnSumShouldKeepMaximum() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.recordTurnSum(100);
        statistics.recordTurnSum(60);
        assertEquals(100, statistics.getMaxPunkteProSpielzug());
    }

    @Test
    void resetGameDataShouldKeepTournamentCounters() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.recordScoringThrow(Segment.TRIPLE_20);
        statistics.resetGameData();
        assertEquals(0, statistics.getAnzWuerfeSpiel());
        assertEquals(0, statistics.getSummeSpiel());
        assertEquals(0.0f, statistics.getAvgSpiel());
        assertEquals(1, statistics.getAnzWuerfeTurnier());
        assertEquals(60, statistics.getSummeTurnier());
        assertEquals(1, statistics.getAnzTripleFeld());
    }
}
