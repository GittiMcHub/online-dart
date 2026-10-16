package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * How precisely a bot throws: the dart lands at the aim point plus an isotropic
 * 2D normal scatter with standard deviation σ (mm).
 */
public class ThrowModel {

    static final int DISTRIBUTION_SAMPLES = 4000;
    private static final long DISTRIBUTION_SEED = 42;

    private final double sigma;
    private final Random random;
    private final Map<AimPoint, Map<Segment, Double>> distributions = new HashMap<>();

    public ThrowModel(double sigma, Random random) {
        this.sigma = sigma;
        this.random = random;
    }

    public double getSigma() {
        return this.sigma;
    }

    public Segment throwAt(AimPoint target) {
        return BoardGeometry.segmentAt(
                target.x() + this.random.nextGaussian() * this.sigma,
                target.y() + this.random.nextGaussian() * this.sigma);
    }

    /** @return probability per segment when aiming at target (Monte Carlo, fixed seed, cached) */
    public synchronized Map<Segment, Double> distribution(AimPoint target) {
        return this.distributions.computeIfAbsent(target, this::sample);
    }

    private Map<Segment, Double> sample(AimPoint target) {
        Random sampler = new Random(DISTRIBUTION_SEED);
        Map<Segment, Double> result = new EnumMap<>(Segment.class);
        double weight = 1.0 / DISTRIBUTION_SAMPLES;
        for (int i = 0; i < DISTRIBUTION_SAMPLES; i++) {
            Segment hit = BoardGeometry.segmentAt(
                    target.x() + sampler.nextGaussian() * this.sigma,
                    target.y() + sampler.nextGaussian() * this.sigma);
            result.merge(hit, weight, Double::sum);
        }
        if (this.sigma == 0) {
            // exact 1.0 instead of 4000 summed fractions
            result.replaceAll((segment, probability) -> 1.0);
        }
        return Collections.unmodifiableMap(result);
    }
}
