package it.tobaben.dart.game.bot;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameSnapshot;
import it.tobaben.dart.game.board.Segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * X01 tactics. Naive bots score on T20 and walk down to a double; smart bots
 * follow the checkout table, set up preferred doubles and on the last dart of
 * a turn avoid leaving a penalized rest (Schnapszahl, house rule) or a bogey
 * number.
 */
public class X01Strategy implements BotStrategy {

    static final List<Integer> PREFERRED_LEAVES =
            List.of(40, 32, 36, 24, 16, 20, 8, 12, 28, 38, 34, 30, 26, 22, 18, 14, 10, 6, 4, 2);
    static final Set<Integer> BOGEY_NUMBERS = Set.of(169, 168, 166, 165, 163, 162, 159);
    static final double MAX_PENALTY_RISK = 0.15;
    static final double BOGEY_WEIGHT = 0.5;
    private static final List<Segment> SCORING_ALTERNATIVES = List.of(
            Segment.TRIPLE_20, Segment.TRIPLE_19, Segment.TRIPLE_18,
            Segment.SINGLE_20, Segment.SINGLE_19, Segment.SINGLE_18);

    private final boolean smart;
    private final ThrowModel throwModel;
    private final IntPredicate penalizedRest;

    /**
     * @param penalizedRest rests that cost a penalty at the end of a turn
     *                      (Schnapszahl when the house rule is on), evaluated by smart bots only
     */
    public X01Strategy(boolean smart, ThrowModel throwModel, IntPredicate penalizedRest) {
        this.smart = smart;
        this.throwModel = throwModel;
        this.penalizedRest = penalizedRest;
    }

    @Override
    public AimPoint aim(GameSnapshot snapshot, Player self) {
        return BoardGeometry.centerOf(target(snapshot, self));
    }

    Segment target(GameSnapshot snapshot, Player self) {
        Map<String, String> rules = snapshot.modeData();
        int rest = snapshot.scores().get(self);
        int dartsLeft = snapshot.throwsLeftInTurn();
        boolean doubleIn = Boolean.parseBoolean(rules.get("doubleIn"));
        boolean doubleOut = Boolean.parseBoolean(rules.get("doubleOut"));
        int startScore = Integer.parseInt(rules.get("startScore"));

        if (doubleIn && rest == startScore) {
            return Segment.DOUBLE_20;
        }
        if (!this.smart) {
            return doubleOut ? naiveDoubleOut(rest) : straightOut(rest);
        }
        Segment planned = doubleOut ? smartDoubleOut(rest, dartsLeft) : straightOut(rest);
        return dartsLeft == 1 ? avoidPenalties(planned, rest, doubleOut) : planned;
    }

    private static Segment straightOut(int rest) {
        if (rest > 60) {
            return Segment.TRIPLE_20;
        }
        Segment exact = oneDartExact(rest);
        return exact != null ? exact : BoardGeometry.segment(1, Math.min(20, rest - 1));
    }

    private static Segment oneDartExact(int rest) {
        if (rest <= 20) {
            return BoardGeometry.segment(1, rest);
        }
        if (rest == 25) {
            return Segment.BULL;
        }
        if (rest == 50) {
            return Segment.BULLS_EYE;
        }
        if (rest % 3 == 0 && rest <= 60) {
            return BoardGeometry.segment(3, rest / 3);
        }
        if (rest % 2 == 0 && rest <= 40) {
            return BoardGeometry.segment(2, rest / 2);
        }
        return null;
    }

    private static Segment naiveDoubleOut(int rest) {
        if (rest > 60) {
            return Segment.TRIPLE_20;
        }
        if (rest == 50) {
            return Segment.BULLS_EYE;
        }
        if (rest % 2 == 0 && rest <= 40) {
            return BoardGeometry.segment(2, rest / 2);
        }
        if (rest > 40) {
            return BoardGeometry.segment(1, rest - 40);
        }
        return Segment.SINGLE_1; // odd rest: make it even
    }

    private static Segment smartDoubleOut(int rest, int dartsLeft) {
        Optional<List<Segment>> route = Checkouts.route(rest, dartsLeft);
        if (route.isPresent()) {
            return route.get().get(0);
        }
        if (rest <= 100) {
            Segment setup = setupFor(rest);
            if (setup != null) {
                return setup;
            }
        }
        return rest - 60 >= 2 ? Segment.TRIPLE_20 : Segment.SINGLE_20;
    }

    /** @return a dart that leaves a preferred double; singles before bull before triples */
    private static Segment setupFor(int rest) {
        for (int leave : PREFERRED_LEAVES) {
            int value = rest - leave;
            if (value >= 1 && value <= 20) {
                return BoardGeometry.segment(1, value);
            }
            if (value == 25) {
                return Segment.BULL;
            }
            if (value >= 3 && value <= 60 && value % 3 == 0) {
                return BoardGeometry.segment(3, value / 3);
            }
        }
        return null;
    }

    private Segment avoidPenalties(Segment planned, int rest, boolean doubleOut) {
        List<Segment> candidates = new ArrayList<>();
        candidates.add(planned);
        for (Segment alternative : SCORING_ALTERNATIVES) {
            // no deliberate bust (v1): alternatives must not bust on a direct hit
            if (!candidates.contains(alternative) && !bustsOnHit(alternative, rest, doubleOut)) {
                candidates.add(alternative);
            }
        }
        Segment best = planned;
        double bestRisk = Double.MAX_VALUE;
        for (Segment candidate : candidates) {
            double risk = penaltyRisk(candidate, rest, doubleOut);
            if (risk < MAX_PENALTY_RISK) {
                return candidate;
            }
            if (risk < bestRisk) {
                best = candidate;
                bestRisk = risk;
            }
        }
        return best;
    }

    double penaltyRisk(Segment target, int rest, boolean doubleOut) {
        double risk = 0;
        for (Map.Entry<Segment, Double> outcome
                : this.throwModel.distribution(BoardGeometry.centerOf(target)).entrySet()) {
            Segment hit = outcome.getKey();
            if (bustsOnHit(hit, rest, doubleOut)) {
                continue; // bust: score back to turn start, no penalty
            }
            int after = rest - hit.getScore();
            if (this.penalizedRest.test(after)) {
                risk += outcome.getValue();
            } else if (doubleOut && BOGEY_NUMBERS.contains(after)) {
                risk += BOGEY_WEIGHT * outcome.getValue();
            }
        }
        return risk;
    }

    private static boolean bustsOnHit(Segment hit, int rest, boolean doubleOut) {
        int after = rest - hit.getScore();
        if (after < 0) {
            return true;
        }
        return doubleOut && (after == 1 || (after == 0 && hit.getMultiplier() != 2));
    }
}
