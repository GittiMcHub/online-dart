package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyCalibrationTest {

    @Test
    void edgesOfTheScale() {
        assertEquals(0.0, DifficultyCalibration.sigmaFor(180));
        assertEquals(2000.0, DifficultyCalibration.sigmaFor(0));
        assertThrows(IllegalArgumentException.class, () -> DifficultyCalibration.sigmaFor(-1));
        assertThrows(IllegalArgumentException.class, () -> DifficultyCalibration.sigmaFor(181));
    }

    @Test
    void tableIsMonotonic() {
        double[][] table = DifficultyCalibration.TABLE;
        for (int i = 1; i < table.length; i++) {
            assertTrue(table[i][0] > table[i - 1][0], "sigma ascending at row " + i);
            assertTrue(table[i][1] <= table[i - 1][1], "average non-increasing at row " + i);
        }
        for (int average = 0; average < 180; average++) {
            assertTrue(DifficultyCalibration.sigmaFor(average) >= DifficultyCalibration.sigmaFor(average + 1),
                    "sigma must not grow with the average at " + average);
        }
    }

    /** Re-measures the constant table: guards against geometry changes. */
    @Test
    void measuredAverageMatchesTheTable() {
        AimPoint t20 = BoardGeometry.centerOf(Segment.TRIPLE_20);
        for (int average : new int[]{10, 25, 40, 60, 85, 120}) {
            ThrowModel model = new ThrowModel(DifficultyCalibration.sigmaFor(average), new Random(average));
            int darts = 90_000;
            long sum = 0;
            for (int i = 0; i < darts; i++) {
                sum += model.throwAt(t20).getScore();
            }
            assertEquals(average, 3.0 * sum / darts, 2.0, "average " + average);
        }
    }

    @Test
    void botProfile() {
        assertTrue(new BotProfile(40).smart());
        assertFalse(new BotProfile(39).smart());
        assertEquals(DifficultyCalibration.sigmaFor(60), new BotProfile(60).sigma());
        assertThrows(IllegalArgumentException.class, () -> new BotProfile(200));
    }
}
