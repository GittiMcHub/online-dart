package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ThrowModelTest {

    private static final AimPoint T20 = BoardGeometry.centerOf(Segment.TRIPLE_20);

    @Test
    void zeroSigmaAlwaysHitsTheTarget() {
        ThrowModel model = new ThrowModel(0, new Random(1));
        for (int i = 0; i < 100; i++) {
            assertEquals(Segment.TRIPLE_20, model.throwAt(T20));
        }
        assertEquals(Map.of(Segment.TRIPLE_20, 1.0), model.distribution(T20));
    }

    @Test
    void distributionSumsToOneAndIsCached() {
        ThrowModel model = new ThrowModel(30, new Random(1));
        Map<Segment, Double> distribution = model.distribution(T20);
        double sum = distribution.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 1e-9);
        assertTrue(distribution.get(Segment.SINGLE_20) > distribution.get(Segment.TRIPLE_20));
        assertSame(distribution, model.distribution(T20));
    }

    @Test
    void sameSeedSameThrows() {
        ThrowModel first = new ThrowModel(25, new Random(7));
        ThrowModel second = new ThrowModel(25, new Random(7));
        for (int i = 0; i < 50; i++) {
            assertEquals(first.throwAt(T20), second.throwAt(T20));
        }
    }
}
