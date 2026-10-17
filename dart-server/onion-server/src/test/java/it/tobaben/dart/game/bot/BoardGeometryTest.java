package it.tobaben.dart.game.bot;

import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardGeometryTest {

    private static Segment at(double radius, double degreesClockwiseFromTop) {
        double radians = Math.toRadians(degreesClockwiseFromTop);
        return BoardGeometry.segmentAt(radius * Math.sin(radians), radius * Math.cos(radians));
    }

    @Test
    void bullAreas() {
        assertEquals(Segment.BULLS_EYE, BoardGeometry.segmentAt(0, 0));
        assertEquals(Segment.BULL, BoardGeometry.segmentAt(0, 10));
    }

    @Test
    void ringsOfTheTwenty() {
        assertEquals(Segment.SINGLE_20, BoardGeometry.segmentAt(0, 50));
        assertEquals(Segment.TRIPLE_20, BoardGeometry.segmentAt(0, 103));
        assertEquals(Segment.SINGLE_20, BoardGeometry.segmentAt(0, 130));
        assertEquals(Segment.DOUBLE_20, BoardGeometry.segmentAt(0, 166));
        assertEquals(Segment.BOARD_HIT, BoardGeometry.segmentAt(0, 171));
    }

    @Test
    void sectorsAroundTheBoard() {
        assertEquals(Segment.TRIPLE_6, BoardGeometry.segmentAt(103, 0));
        assertEquals(Segment.TRIPLE_3, BoardGeometry.segmentAt(0, -103));
        assertEquals(Segment.TRIPLE_11, BoardGeometry.segmentAt(-103, 0));
    }

    @Test
    void sectorBoundaryBetweenTwentyAndOne() {
        assertEquals(Segment.SINGLE_20, at(130, 8.9));
        assertEquals(Segment.SINGLE_1, at(130, 9.1));
        assertEquals(Segment.SINGLE_20, at(130, -8.9));
        assertEquals(Segment.SINGLE_5, at(130, -9.1));
    }

    @Test
    void centerOfEveryAimableSegmentLiesInThatSegment() {
        for (Segment segment : Segment.values()) {
            if (segment == Segment.WALL_HIT || segment == Segment.BOARD_HIT || segment == Segment.BULL) {
                continue;
            }
            AimPoint center = BoardGeometry.centerOf(segment);
            assertEquals(segment, BoardGeometry.segmentAt(center.x(), center.y()), segment.name());
        }
        // bull is aimed at the centre (the bullseye counts for it too)
        assertEquals(AimPoint.CENTER, BoardGeometry.centerOf(Segment.BULL));
    }

    @Test
    void centerOfRejectsNonAimableSegments() {
        assertThrows(IllegalArgumentException.class, () -> BoardGeometry.centerOf(Segment.BOARD_HIT));
        assertThrows(IllegalArgumentException.class, () -> BoardGeometry.centerOf(Segment.WALL_HIT));
    }

    @Test
    void segmentLookup() {
        assertEquals(Segment.TRIPLE_20, BoardGeometry.segment(3, 20));
        assertEquals(Segment.DOUBLE_16, BoardGeometry.segment(2, 16));
        assertEquals(Segment.SINGLE_1, BoardGeometry.segment(1, 1));
        assertEquals(Segment.BULL, BoardGeometry.segment(1, 25));
        assertEquals(Segment.BULLS_EYE, BoardGeometry.segment(2, 25));
        assertThrows(IllegalArgumentException.class, () -> BoardGeometry.segment(3, 25));
    }
}
