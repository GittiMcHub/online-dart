package it.tobaben.dart.game.bot;

/**
 * Maps the difficulty a host picks (3-dart scoring average 0–180) to the
 * scatter σ of the ThrowModel. The table depends only on the board geometry
 * and the T20 aim point, so it is a constant: generated once by Monte Carlo
 * (2,000,000 darts per row aimed at the T20 centre). DifficultyCalibrationTest
 * re-measures it.
 */
public final class DifficultyCalibration {

    /** {σ in mm, 3-dart average when always aiming at T20}, σ ascending. */
    static final double[][] TABLE = {
            {0.0, 180.0},
            {1.0, 180.0},
            {2.0, 174.5},
            {3.0, 158.1},
            {4.0, 142.0},
            {5.0, 129.0},
            {6.0, 118.7},
            {7.0, 109.9},
            {8.0, 102.1},
            {9.0, 95.0},
            {10.0, 88.5},
            {12.0, 77.5},
            {14.0, 68.6},
            {16.0, 61.6},
            {18.0, 56.0},
            {20.0, 51.8},
            {22.0, 48.4},
            {25.0, 44.7},
            {28.0, 42.2},
            {31.0, 40.4},
            {35.0, 38.4},
            {40.0, 36.5},
            {45.0, 34.9},
            {50.0, 33.4},
            {60.0, 30.8},
            {70.0, 28.4},
            {80.0, 26.3},
            {90.0, 24.4},
            {100.0, 22.6},
            {120.0, 19.3},
            {140.0, 16.5},
            {170.0, 13.0},
            {200.0, 10.3},
            {250.0, 7.3},
            {300.0, 5.4},
            {400.0, 3.2},
            {600.0, 1.5},
            {1000.0, 0.5},
            {2000.0, 0.1}
    };

    private DifficultyCalibration() {
    }

    /** @return σ for the average, linearly interpolated; averages below the table's end get its largest σ */
    public static double sigmaFor(int average) {
        if (average < 0 || average > 180) {
            throw new IllegalArgumentException("Average muss zwischen 0 und 180 liegen: " + average);
        }
        for (int i = 1; i < TABLE.length; i++) {
            double upperAverage = TABLE[i - 1][1];
            double lowerAverage = TABLE[i][1];
            if (average >= lowerAverage) {
                if (upperAverage == lowerAverage) {
                    return TABLE[i - 1][0];
                }
                double t = (upperAverage - average) / (upperAverage - lowerAverage);
                return TABLE[i - 1][0] + t * (TABLE[i][0] - TABLE[i - 1][0]);
            }
        }
        return TABLE[TABLE.length - 1][0];
    }
}
