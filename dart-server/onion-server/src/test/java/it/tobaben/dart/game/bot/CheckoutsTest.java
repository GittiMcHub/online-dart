package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutsTest {

    static final Set<Integer> BOGEY_NUMBERS = Set.of(169, 168, 166, 165, 163, 162, 159);

    @Test
    void everyRouteIsAValidDoubleOutFinish() {
        Map<Integer, List<Segment>> routes = Checkouts.routes();
        for (Map.Entry<Integer, List<Segment>> entry : routes.entrySet()) {
            List<Segment> route = entry.getValue();
            assertTrue(route.size() >= 1 && route.size() <= 3, "darts for " + entry.getKey());
            assertEquals(entry.getKey(), route.stream().mapToInt(Segment::getScore).sum(), "sum for " + entry.getKey());
            assertEquals(2, route.get(route.size() - 1).getMultiplier(), "double finish for " + entry.getKey());
        }
    }

    @Test
    void coversEveryFinishableRest() {
        Map<Integer, List<Segment>> routes = Checkouts.routes();
        for (int rest = 2; rest <= 170; rest++) {
            assertEquals(!BOGEY_NUMBERS.contains(rest), routes.containsKey(rest), "rest " + rest);
        }
        assertFalse(routes.containsKey(171));
    }

    @Test
    void routeRespectsTheDartsLeft() {
        assertEquals(Optional.of(List.of(Segment.SINGLE_10, Segment.DOUBLE_20)), Checkouts.route(50, 3));
        // one dart left: single-dart finishes outside the preferred route still count
        assertEquals(Optional.of(List.of(Segment.BULLS_EYE)), Checkouts.route(50, 1));
        assertEquals(Optional.of(List.of(Segment.DOUBLE_16)), Checkouts.route(32, 1));
        assertEquals(Optional.empty(), Checkouts.route(110, 2));
        assertEquals(Optional.empty(), Checkouts.route(171, 3));
        assertEquals(Optional.empty(), Checkouts.route(3, 1));
    }
}
