package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.game.board.Segment;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The only place that knows the 3-digit dartboard wire protocol (see root README):
 * first digit multiplier, last two digits field value ("320" = triple 20,
 * "125" = bull, "225" = bullseye). Special codes: 996 bounce out, 997 wall hit,
 * 998 miss, 999 next. Everything else is rejected.
 */
public final class SegmentCodec {

    private static final Map<Integer, Segment> BY_CODE = new HashMap<>();

    static {
        for (Segment segment : Segment.values()) {
            if (segment.getMultiplier() > 0) {
                int field = segment.getScore() / segment.getMultiplier();
                BY_CODE.put(segment.getMultiplier() * 100 + field, segment);
            }
        }
    }

    private SegmentCodec() {
    }

    /**
     * Decodes one raw dartboard message into a DartboardInput.
     *
     * @return empty for anything that is not a valid protocol message
     */
    public static Optional<DartboardInput> decode(int dartboardId, String message) {
        if (message == null || message.length() != 3 || !message.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        switch (message) {
            case "999":
                return Optional.of(DartboardInput.next(dartboardId));
            case "996":
                return Optional.of(DartboardInput.bounceOut(dartboardId));
            case "997":
                return Optional.of(DartboardInput.ofThrow(dartboardId, Segment.WALL_HIT));
            case "998":
                return Optional.of(DartboardInput.ofThrow(dartboardId, Segment.BOARD_HIT));
            default:
                Segment segment = BY_CODE.get(Integer.parseInt(message));
                return segment == null
                        ? Optional.empty()
                        : Optional.of(DartboardInput.ofThrow(dartboardId, segment));
        }
    }
}
