package it.tobaben.dart.application;

import it.tobaben.dart.game.board.Segment;

/**
 * A single decoded dartboard message. The 3-digit wire protocol (see root README)
 * is translated into this model by the infrastructure (SegmentCodec): regular
 * hits, wall hits (997) and misses (998) become THROW with a Segment; 999 becomes
 * NEXT and 996 becomes BOUNCE_OUT (both without a segment).
 */
public record DartboardInput(int dartboardId, Type type, Segment segment) {

    public enum Type {
        THROW, NEXT, BOUNCE_OUT
    }

    public static DartboardInput ofThrow(int dartboardId, Segment segment) {
        return new DartboardInput(dartboardId, Type.THROW, segment);
    }

    public static DartboardInput next(int dartboardId) {
        return new DartboardInput(dartboardId, Type.NEXT, null);
    }

    public static DartboardInput bounceOut(int dartboardId) {
        return new DartboardInput(dartboardId, Type.BOUNCE_OUT, null);
    }
}
