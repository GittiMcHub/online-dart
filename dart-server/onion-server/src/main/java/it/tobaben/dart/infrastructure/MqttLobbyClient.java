package it.tobaben.dart.infrastructure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.tobaben.dart.application.client.ClientSession;
import it.tobaben.dart.application.client.port.LobbyClientPort;

/**
 * Client side of the MQTT lobby: publishes join/leave requests and feeds
 * lobby/state and this client's lobby/response messages into the
 * ClientSession. The Last-Will (remove all players of this client) must be
 * set on the MqttAdapter before connecting — see {@link #lastWillPayload}.
 */
public class MqttLobbyClient implements LobbyClientPort {

    private final MqttAdapter mqtt;
    private final ClientSession session;

    public MqttLobbyClient(MqttAdapter mqtt, ClientSession session) {
        this.mqtt = mqtt;
        this.session = session;
    }

    public static String lastWillPayload(String clientId) {
        return LobbyJson.leaveJson("", clientId, MqttLobbyAdapter.ALL_PLAYERS);
    }

    public void start() {
        this.mqtt.registerHandler(MqttLobbyAdapter.STATE_TOPIC, 1,
                (topic, payload) -> this.session.onLobbyState(LobbyJson.parseState(payload)));
        this.mqtt.registerHandler(MqttLobbyAdapter.RESPONSE_TOPIC_PREFIX + this.session.getClientId(), 1,
                (topic, payload) -> handleResponse(payload));
        this.session.setPort(this);
    }

    private void handleResponse(String payload) {
        JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
        this.session.onResponse(
                json.get("requestId").getAsString(),
                json.get("ok").getAsBoolean(),
                json.has("error") ? json.get("error").getAsString() : null);
    }

    @Override
    public void publishJoin(String requestId, String clientId, String playerName, int dartboardId) {
        this.mqtt.publish(MqttLobbyAdapter.JOIN_TOPIC,
                LobbyJson.joinJson(requestId, clientId, playerName, dartboardId), 1, false);
    }

    @Override
    public void publishLeave(String requestId, String clientId, String playerName) {
        this.mqtt.publish(MqttLobbyAdapter.LEAVE_TOPIC,
                LobbyJson.leaveJson(requestId, clientId, playerName), 1, false);
    }
}
