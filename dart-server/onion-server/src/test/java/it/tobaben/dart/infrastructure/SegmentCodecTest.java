package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SegmentCodecTest {

    private static Segment decodeSegment(String message) {
        return SegmentCodec.decode(1, message).orElseThrow().segment();
    }

    @Test
    void decodesSinglesDoublesTriplesPerReadmeTable() {
        assertEquals(Segment.SINGLE_1, decodeSegment("101"));
        assertEquals(Segment.SINGLE_20, decodeSegment("120"));
        assertEquals(Segment.DOUBLE_1, decodeSegment("201"));
        assertEquals(Segment.DOUBLE_20, decodeSegment("220"));
        assertEquals(Segment.TRIPLE_1, decodeSegment("301"));
        assertEquals(Segment.TRIPLE_20, decodeSegment("320"));
        assertEquals(Segment.BULL, decodeSegment("125"));
        assertEquals(Segment.BULLS_EYE, decodeSegment("225"));
    }

    @Test
    void decodesEveryRegularFieldBothWays() {
        // 1-20 in all three multipliers must decode to a segment with matching score
        for (int multiplier = 1; multiplier <= 3; multiplier++) {
            for (int field = 1; field <= 20; field++) {
                String message = String.format("%d%02d", multiplier, field);
                Segment segment = decodeSegment(message);
                assertEquals(multiplier * field, segment.getScore(), "message " + message);
                assertEquals(multiplier, segment.getMultiplier(), "message " + message);
            }
        }
    }

    @Test
    void decodesSpecialCodes() {
        assertEquals(DartboardInput.Type.NEXT, SegmentCodec.decode(3, "999").orElseThrow().type());
        assertEquals(DartboardInput.Type.BOUNCE_OUT, SegmentCodec.decode(3, "996").orElseThrow().type());

        DartboardInput wall = SegmentCodec.decode(3, "997").orElseThrow();
        assertEquals(DartboardInput.Type.THROW, wall.type());
        assertEquals(Segment.WALL_HIT, wall.segment());

        DartboardInput miss = SegmentCodec.decode(3, "998").orElseThrow();
        assertEquals(DartboardInput.Type.THROW, miss.type());
        assertEquals(Segment.BOARD_HIT, miss.segment());
    }

    @Test
    void keepsDartboardId() {
        assertEquals(7, SegmentCodec.decode(7, "120").orElseThrow().dartboardId());
    }

    @Test
    void rejectsInvalidMessages() {
        assertEquals(Optional.empty(), SegmentCodec.decode(1, null));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, ""));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "12"));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "1200"));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "abc"));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "000"));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "121")); // single 21 does not exist
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "226")); // double 26 does not exist
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "325")); // triple bull does not exist
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "421"));
        assertEquals(Optional.empty(), SegmentCodec.decode(1, "-99"));
    }
}
