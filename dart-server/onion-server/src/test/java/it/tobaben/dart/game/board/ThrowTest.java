package it.tobaben.dart.game.board;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrowTest {

    @Test
    void shouldReturnRightScore() {
        Throw testThrow = new Throw(null, Segment.DOUBLE_2);
        assertEquals(4, testThrow.getScore());
    }
}