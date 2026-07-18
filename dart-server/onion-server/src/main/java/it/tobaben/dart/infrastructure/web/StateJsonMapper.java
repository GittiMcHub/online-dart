package it.tobaben.dart.infrastructure.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.application.board.BoardService;
import it.tobaben.dart.application.board.DiscoveredDartboard;
import it.tobaben.dart.application.client.LobbyStateView;
import it.tobaben.dart.application.lobby.LobbyPlayer;
import it.tobaben.dart.application.session.ServerSession;
import it.tobaben.dart.application.session.TournamentConfig;
import it.tobaben.dart.infrastructure.AppContext;
import it.tobaben.dart.infrastructure.MqttConfig;

import java.util.List;

/**
 * Renders the full application state for /api/state and the SSE events: mode,
 * broker status, session phase, lobby, tournament config, last results and the
 * last published gameUpdate (as nested JSON).
 */
public final class StateJsonMapper {

    private static final Gson GSON = new Gson();

    private StateJsonMapper() {
    }

    public static String toJson(AppContext context) {
        ServerSession session = context.getSession();
        JsonObject root = new JsonObject();
        root.addProperty("mode", context.getConfig().mode().name());
        root.addProperty("serverName", context.getConfig().serverName());
        root.addProperty("phase", session.getPhase().name());

        JsonObject broker = new JsonObject();
        broker.addProperty("configured", context.isBrokerConfigured());
        broker.addProperty("embedded", context.isEmbeddedActive());
        broker.addProperty("running", context.isEmbeddedBrokerActive());
        broker.addProperty("connected", context.isMqttConnected());
        MqttConfig mqtt = context.getActiveMqttConfig() != null
                ? context.getActiveMqttConfig()
                : context.getConfig().mqtt();
        broker.addProperty("host", mqtt.host());
        broker.addProperty("port", mqtt.port());
        broker.addProperty("username", mqtt.username());
        broker.addProperty("wsPort", context.getEmbeddedWsPort());
        root.add("broker", broker);

        JsonArray lobby = new JsonArray();
        for (LobbyPlayer player : session.getLobby().getPlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", player.name());
            entry.addProperty("dartboardId", player.dartboardId());
            entry.addProperty("clientId", player.clientId());
            entry.addProperty("local", player.isLocal());
            lobby.add(entry);
        }
        root.add("lobby", lobby);

        TournamentConfig config = session.getGameConfig();
        if (config == null) {
            root.add("gameConfig", null);
        } else {
            root.add("gameConfig", GSON.toJsonTree(config));
        }

        JsonArray results = new JsonArray();
        for (List<RegisteredPlayer> ranking : session.getLastResults()) {
            JsonArray game = new JsonArray();
            for (RegisteredPlayer player : ranking) {
                game.add(player.getName());
            }
            results.add(game);
        }
        root.add("lastResults", results);

        if (context.getConfig().mode().actsAsClient()) {
            JsonObject boards = new JsonObject();
            boards.addProperty("scanning", context.getBoardService().isScanning());
            if (context.getBoardService().getLastError() != null) {
                boards.addProperty("error", context.getBoardService().getLastError());
            }
            JsonArray discovered = new JsonArray();
            for (DiscoveredDartboard found : context.getBoardService().getLastScan()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("mac", found.mac());
                entry.addProperty("name", found.name());
                entry.addProperty("likelyDartboard", found.looksLikeDartboard());
                discovered.add(entry);
            }
            boards.add("discovered", discovered);
            JsonArray connected = new JsonArray();
            for (BoardService.ManagedBoard board : context.getBoardService().getBoards()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("mac", board.mac());
                entry.addProperty("name", board.name());
                entry.addProperty("dartboardId", board.dartboardId());
                entry.addProperty("status", board.status().name());
                connected.add(entry);
            }
            boards.add("connected", connected);
            root.add("boards", boards);

            JsonObject client = new JsonObject();
            client.addProperty("clientId", context.getClientSession().getClientId());
            LobbyStateView remote = context.getClientSession().getRemoteLobby();
            if (remote != null) {
                JsonObject remoteLobby = new JsonObject();
                remoteLobby.addProperty("phase", remote.phase());
                remoteLobby.addProperty("serverName", remote.serverName());
                JsonArray players = new JsonArray();
                for (LobbyPlayer player : remote.players()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", player.name());
                    entry.addProperty("dartboardId", player.dartboardId());
                    entry.addProperty("mine", player.clientId()
                            .equals(context.getClientSession().getClientId()));
                    players.add(entry);
                }
                remoteLobby.add("players", players);
                client.add("remoteLobby", remoteLobby);
            }
            root.add("client", client);
        }

        root.add("game", JsonParser.parseString(context.getLastGameJson()));
        return GSON.toJson(root);
    }
}
