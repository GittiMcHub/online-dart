package it.tobaben.dart.application.client.port;

/**
 * Outbound port of the client mode: sends lobby requests to the remote server
 * (infrastructure maps them to the lobby/join and lobby/leave MQTT topics).
 */
public interface LobbyClientPort {

    void publishJoin(String requestId, String clientId, String playerName, int dartboardId);

    void publishLeave(String requestId, String clientId, String playerName);
}
