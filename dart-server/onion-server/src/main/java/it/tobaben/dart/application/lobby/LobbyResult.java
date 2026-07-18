package it.tobaben.dart.application.lobby;

/**
 * Outcome of a lobby operation; {@code error} carries the German message shown
 * to the user (web UI or lobby/response) when {@code ok} is false.
 */
public record LobbyResult(boolean ok, String error) {

    public static LobbyResult success() {
        return new LobbyResult(true, null);
    }

    public static LobbyResult failure(String error) {
        return new LobbyResult(false, error);
    }
}
