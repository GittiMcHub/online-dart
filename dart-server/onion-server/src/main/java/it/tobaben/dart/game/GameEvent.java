package it.tobaben.dart.game;

/**
 * Domain events emitted by a game while playing turns. The game logic never talks to
 * MQTT or sounds directly; outer layers map these events (e.g. to status/playSound).
 */
public enum GameEvent {
    /** First throw of the game happened, the game is now running. */
    GAME_STARTED,
    /** A dart was thrown and evaluated (details derivable from the thrown Segment). */
    THROW,
    /** The current player busted, the turn score is voided. */
    BUST,
    /** The current player reached the target score and is finished. */
    PLAYER_FINISHED,
    /** A player scored the maximum of 180 points in one turn. */
    MAX_POINTS,
    /** The current turn is complete (all darts thrown, early end, bust or abort). */
    TURN_ENDED,
    /** The game is over, the ranking is final. */
    GAME_OVER
}
