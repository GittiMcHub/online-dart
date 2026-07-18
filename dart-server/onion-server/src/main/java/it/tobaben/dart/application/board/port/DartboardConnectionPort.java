package it.tobaben.dart.application.board.port;

import it.tobaben.dart.application.board.BoardStatus;

import java.util.function.Consumer;

/**
 * Outbound port: one live connection to a dartboard. The implementation
 * translates board notifications into the 3-digit wire codes (the same codes
 * SegmentCodec decodes) and keeps reconnecting until {@link #close()}.
 */
public interface DartboardConnectionPort extends AutoCloseable {

    /** Starts connecting (async); codes and status changes arrive on the consumers. */
    void open(Consumer<String> onWireCode, Consumer<BoardStatus> onStatus);

    @Override
    void close();
}
