package it.tobaben.dart.infrastructure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.tobaben.dart.application.client.LobbyStateView;
import it.tobaben.dart.application.lobby.LobbyPlayer;
import it.tobaben.dart.application.session.ServerSession;
import it.tobaben.dart.application.session.TournamentConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON (de)serialization of the lobby topics — the only place that knows the
 * wire format of lobby/state, lobby/join, lobby/leave and lobby/response.
 */
public final class LobbyJson {

    private LobbyJson() {
    }

    /* ---------- lobby/state ---------- */

    public static String stateJson(ServerSession session, String serverName, long seq) {
        JsonObject root = new JsonObject();
        root.addProperty("phase", session.getPhase().name());
        root.addProperty("serverName", serverName);
        JsonArray players = new JsonArray();
        for (LobbyPlayer player : session.getLobby().getPlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", player.name());
            entry.addProperty("dartboardId", player.dartboardId());
            entry.addProperty("clientId", player.clientId());
            entry.addProperty("local", player.isLocal());
            players.add(entry);
        }
        root.add("players", players);
        TournamentConfig config = session.getGameConfig();
        if (config != null) {
            JsonObject game = new JsonObject();
            game.addProperty("gameMode", config.gameMode());
            game.addProperty("startScore", config.startScore());
            game.addProperty("games", config.games());
            root.add("gameConfig", game);
        }
        root.addProperty("seq", seq);
        return root.toString();
    }

    public static LobbyStateView parseState(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<LobbyPlayer> players = new ArrayList<>();
        if (root.has("players")) {
            root.getAsJsonArray("players").forEach(element -> {
                JsonObject entry = element.getAsJsonObject();
                players.add(new LobbyPlayer(
                        entry.get("name").getAsString(),
                        entry.get("dartboardId").getAsInt(),
                        entry.get("clientId").getAsString()));
            });
        }
        return new LobbyStateView(
                root.get("phase").getAsString(),
                root.has("serverName") ? root.get("serverName").getAsString() : "",
                players,
                root.has("seq") ? root.get("seq").getAsLong() : 0);
    }

    /* ---------- lobby/join + lobby/leave ---------- */

    public static String joinJson(String requestId, String clientId, String playerName, int dartboardId) {
        JsonObject json = requestBase(requestId, clientId, playerName);
        json.addProperty("dartboardId", dartboardId);
        return json.toString();
    }

    public static String leaveJson(String requestId, String clientId, String playerName) {
        return requestBase(requestId, clientId, playerName).toString();
    }

    private static JsonObject requestBase(String requestId, String clientId, String playerName) {
        JsonObject json = new JsonObject();
        json.addProperty("requestId", requestId);
        json.addProperty("clientId", clientId);
        json.addProperty("playerName", playerName);
        return json;
    }

    /* ---------- lobby/response/<clientId> ---------- */

    public static String responseJson(String requestId, boolean ok, String error) {
        JsonObject json = new JsonObject();
        json.addProperty("requestId", requestId);
        json.addProperty("ok", ok);
        if (error != null) {
            json.addProperty("error", error);
        }
        return json.toString();
    }
}
