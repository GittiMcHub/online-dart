package it.tobaben.dart.application;

/**
 * Engine state machine, string values identical to the anthrax GameState for
 * client compatibility (schema.json field "gameState").
 */
public enum EngineState {
    UNDEFINED,
    /** The current player may throw. */
    RUNNING,
    /** Turn ended, waiting for the NEXT button (bounce out may still undo the turn). */
    WAITING,
    /** Game over, waiting for the final NEXT button. */
    FINISHED
}
