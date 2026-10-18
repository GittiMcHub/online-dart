package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.board.port.ThrowPublisherPort;
import it.tobaben.dart.application.bot.port.VirtualBoardPort;
import it.tobaben.dart.game.board.Segment;

/** Bot darts → wire codes → ThrowPublisherPort (dartboard/&lt;id&gt; via the broker). */
public class MqttVirtualBoard implements VirtualBoardPort {

    private static final String NEXT = "999";

    private final ThrowPublisherPort publisher;

    public MqttVirtualBoard(ThrowPublisherPort publisher) {
        this.publisher = publisher;
    }

    @Override
    public void throwDart(int dartboardId, Segment segment) {
        this.publisher.publishThrow(dartboardId, SegmentCodec.encode(segment));
    }

    @Override
    public void next(int dartboardId) {
        this.publisher.publishThrow(dartboardId, NEXT);
    }
}
