package it.tobaben.dart.infrastructure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.tobaben.dart.application.lobby.LobbyResult;
import it.tobaben.dart.application.session.ServerSession;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Server side of the MQTT lobby: answers lobby/join and lobby/leave requests
 * and broadcasts the lobby state retained on lobby/state after every change,
 * so late joiners and displays see the lineup immediately. The seq counter
 * starts at the wall clock so a restarted server always outbids its own stale
 * retained state.
 */
public class MqttLobbyAdapter {

    public static final String STATE_TOPIC = "lobby/state";
    public static final String JOIN_TOPIC = "lobby/join";
    public static final String LEAVE_TOPIC = "lobby/leave";
    public static final String RESPONSE_TOPIC_PREFIX = "lobby/response/";

    /** playerName in a lobby/leave that removes every player of the client (Last-Will). */
    public static final String ALL_PLAYERS = "*";

    private final MqttAdapter mqtt;
    private final ServerSession session;
    private final String serverName;
    private final AtomicLong seq = new AtomicLong(System.currentTimeMillis());
    // the session outlives broker reconfigurations, its listener list only
    // grows — a detached adapter must stop publishing on the dead connection
    private volatile boolean detached;

    public MqttLobbyAdapter(MqttAdapter mqtt, ServerSession session, String serverName) {
        this.mqtt = mqtt;
        this.session = session;
        this.serverName = serverName;
    }

    public void start() {
        this.mqtt.registerHandler(JOIN_TOPIC, 1, (topic, payload) -> handleJoin(payload));
        this.mqtt.registerHandler(LEAVE_TOPIC, 1, (topic, payload) -> handleLeave(payload));
        this.session.addChangeListener(this::publishState);
        publishState();
    }

    /** Leaves a retained IDLE state behind so clients see the lobby as closed. */
    public void shutdown() {
        if (this.detached) {
            return;
        }
        JsonObject closed = new JsonObject();
        closed.addProperty("phase", "IDLE");
        closed.addProperty("serverName", this.serverName);
        closed.add("players", new JsonArray());
        closed.addProperty("seq", this.seq.incrementAndGet());
        this.mqtt.publish(STATE_TOPIC, closed.toString(), 1, true);
    }

    /** Silences this adapter after a broker reconfiguration created a new one. */
    public void detach() {
        this.detached = true;
    }

    private void handleJoin(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String clientId = json.get("clientId").getAsString();
            LobbyResult result = this.session.joinLobby(
                    json.get("playerName").getAsString(),
                    json.get("dartboardId").getAsInt(),
                    clientId);
            respond(clientId, json, result);
        } catch (RuntimeException e) {
            System.err.println("[LOBBY] Ungültige join-Nachricht verworfen: " + e.getMessage());
        }
    }

    private void handleLeave(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String clientId = json.get("clientId").getAsString();
            String playerName = json.get("playerName").getAsString();
            if (ALL_PLAYERS.equals(playerName)) {
                this.session.clientDisconnected(clientId); // Last-Will: no response expected
                return;
            }
            respond(clientId, json, this.session.leaveLobby(playerName, clientId));
        } catch (RuntimeException e) {
            System.err.println("[LOBBY] Ungültige leave-Nachricht verworfen: " + e.getMessage());
        }
    }

    private void respond(String clientId, JsonObject request, LobbyResult result) {
        String requestId = request.has("requestId") ? request.get("requestId").getAsString() : "";
        this.mqtt.publish(RESPONSE_TOPIC_PREFIX + clientId,
                LobbyJson.responseJson(requestId, result.ok(), result.error()), 1, false);
    }

    private void publishState() {
        if (this.detached) {
            return;
        }
        this.mqtt.publish(STATE_TOPIC,
                LobbyJson.stateJson(this.session, this.serverName, this.seq.incrementAndGet()), 1, true);
    }
}
