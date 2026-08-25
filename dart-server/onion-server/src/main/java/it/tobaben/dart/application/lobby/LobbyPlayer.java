package it.tobaben.dart.application.lobby;

/**
 * A player waiting in the lobby. Local players are added by the host via the
 * web UI ({@code clientId} = {@link #LOCAL_CLIENT_ID}), remote players join via
 * MQTT with the random clientId of their client instance.
 */
public record LobbyPlayer(String name, int dartboardId, String clientId) {

    public static final String LOCAL_CLIENT_ID = "local";

    public boolean isLocal() {
        return LOCAL_CLIENT_ID.equals(this.clientId);
    }
}
