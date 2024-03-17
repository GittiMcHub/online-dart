package it.tobaben.dart.game.board;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DartSetTest {

    @Test
    void addOnlyAllowedLimitOfThrow() {
        DartSet testDartSet = new DartSet(null,3);
        testDartSet.addThrow(Segment.BULLS_EYE);
        testDartSet.addThrow(Segment.BULLS_EYE);
        testDartSet.addThrow(Segment.BULLS_EYE);
        testDartSet.addThrow(Segment.BULLS_EYE);
        assertEquals(150, testDartSet.getScore());
    }

    @Test
    void busted() {
        DartSet testDartSet = new DartSet(null,3);
        testDartSet.addThrow(Segment.BULLS_EYE);
        testDartSet.busted();
        assertEquals(0, testDartSet.getScore());
        assertTrue(testDartSet.isDone());
    }

    @Test
    void isDone() {
        DartSet testDartSet = new DartSet(null,3);
        assertFalse(testDartSet.isDone());
        testDartSet.addThrow(Segment.BULLS_EYE);
        assertFalse(testDartSet.isDone());
        testDartSet.addThrow(Segment.BULLS_EYE);
        assertFalse(testDartSet.isDone());
        testDartSet.addThrow(Segment.BULLS_EYE);
        assertTrue(testDartSet.isDone());
    }

    @Test
    void getScore() {
        DartSet testDartSet = new DartSet(null,3);
        testDartSet.addThrow(Segment.DOUBLE_10);
        assertEquals(20, testDartSet.getScore());
        testDartSet.addThrow(Segment.TRIPLE_5);
        assertEquals(35, testDartSet.getScore());
        testDartSet.addThrow(Segment.BULLS_EYE);
        assertEquals(85, testDartSet.getScore());
    }
}