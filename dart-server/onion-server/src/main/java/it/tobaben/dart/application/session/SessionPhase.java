package it.tobaben.dart.application.session;

/**
 * Lifecycle of the long-lived server: no lobby open (IDLE), lobby open and
 * collecting players (LOBBY), tournament running (RUNNING). After a tournament
 * finishes or is aborted the session returns to IDLE.
 */
public enum SessionPhase {
    IDLE,
    LOBBY,
    RUNNING
}
