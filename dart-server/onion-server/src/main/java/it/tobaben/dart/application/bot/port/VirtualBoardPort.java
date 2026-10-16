package it.tobaben.dart.application.bot.port;

import it.tobaben.dart.game.board.Segment;

/**
 * Outbound port: a bot "throws" on its virtual dartboard. The infrastructure
 * turns this into wire codes on dartboard/&lt;id&gt;, so bot darts take the same
 * single input path into the game as real boards.
 */
public interface VirtualBoardPort {

    void throwDart(int dartboardId, Segment segment);

    /** The bot pulls its darts (wire code 999). */
    void next(int dartboardId);
}
