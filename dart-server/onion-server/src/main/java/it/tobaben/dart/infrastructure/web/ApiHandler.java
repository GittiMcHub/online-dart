package it.tobaben.dart.infrastructure.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.application.lobby.LobbyPlayer;
import it.tobaben.dart.application.lobby.LobbyResult;
import it.tobaben.dart.application.session.TournamentConfig;
import it.tobaben.dart.infrastructure.AppContext;
import it.tobaben.dart.infrastructure.MqttConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * The management REST API under /api/ (see README table). All responses are
 * JSON; mutations answer {"ok":true} or {"ok":false,"error":"..."} with the
 * German error message from the application layer.
 */
public class ApiHandler implements HttpHandler {

    private final AppContext context;
    private final SseHub sseHub;

    public ApiHandler(AppContext context, SseHub sseHub) {
        this.context = context;
        this.sseHub = sseHub;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String route = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
        try {
            if ("GET /api/state".equals(route)) {
                respond(exchange, 200, StateJsonMapper.toJson(this.context));
            } else if ("GET /api/events".equals(route)) {
                this.sseHub.subscribe(exchange, StateJsonMapper.toJson(this.context));
                // exchange stays open — SseHub owns the stream now
            } else if ("POST /api/broker".equals(route)) {
                handleBroker(exchange);
            } else if ("POST /api/lobby/open".equals(route)) {
                respondResult(exchange, this.context.getSession().openLobby());
            } else if ("POST /api/lobby/close".equals(route)) {
                respondResult(exchange, this.context.getSession().closeLobby());
            } else if ("POST /api/lobby/players".equals(route)) {
                handleAddPlayer(exchange);
            } else if (exchange.getRequestMethod().equals("POST")
                    && exchange.getRequestURI().getPath().startsWith("/api/lobby/players/")
                    && exchange.getRequestURI().getPath().endsWith("/move")) {
                handleMovePlayer(exchange);
            } else if (exchange.getRequestMethod().equals("DELETE")
                    && exchange.getRequestURI().getPath().startsWith("/api/lobby/players/")) {
                handleRemovePlayer(exchange);
            } else if ("POST /api/tournament/start".equals(route)) {
                handleStartTournament(exchange);
            } else if ("POST /api/tournament/abort".equals(route)) {
                respondResult(exchange, this.context.getSession().abortTournament());
            } else if ("POST /api/client/join".equals(route)) {
                handleClientJoin(exchange);
            } else if ("POST /api/client/leave".equals(route)) {
                handleClientLeave(exchange);
            } else if ("POST /api/boards/scan".equals(route)) {
                respondResult(exchange, this.context.getBoardService().startScan());
            } else if (exchange.getRequestMethod().equals("POST")
                    && exchange.getRequestURI().getPath().startsWith("/api/boards/")) {
                handleBoardAction(exchange);
            } else {
                respond(exchange, 404, error("Unbekannter Endpunkt: " + route));
            }
        } catch (JsonSyntaxException | IllegalArgumentException | IllegalStateException e) {
            respond(exchange, 400, error(e.getMessage()));
        } catch (Exception e) {
            respond(exchange, 500, error("Interner Fehler: " + e.getMessage()));
        }
    }

    private void handleBroker(HttpExchange exchange) throws IOException {
        JsonObject body = readBody(exchange);
        boolean embedded = !body.has("embedded") || body.get("embedded").getAsBoolean();
        MqttConfig defaults = this.context.getConfig().mqtt();
        MqttConfig mqtt = new MqttConfig(
                embedded ? "127.0.0.1" : stringOr(body, "host", defaults.host()),
                intOr(body, "port", defaults.port()),
                stringOr(body, "username", defaults.username()),
                stringOr(body, "password", defaults.password()),
                defaults.clientId(),
                defaults.qos());
        int wsPort = intOr(body, "wsPort", this.context.getConfig().embeddedBrokerWsPort());
        String error = this.context.configureBroker(embedded, mqtt, wsPort);
        respondResult(exchange, error == null ? LobbyResult.success() : LobbyResult.failure(error));
    }

    private void handleAddPlayer(HttpExchange exchange) throws IOException {
        if (!requireBroker(exchange)) {
            return;
        }
        JsonObject body = readBody(exchange);
        String name = stringOr(body, "name", null);
        int dartboardId = intOr(body, "dartboardId", -1);
        respondResult(exchange, this.context.getSession()
                .joinLobby(name, dartboardId, LobbyPlayer.LOCAL_CLIENT_ID));
    }

    /** POST /api/lobby/players/{name}/move mit {"offset": -1|1} */
    private void handleMovePlayer(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String encoded = path.substring("/api/lobby/players/".length(), path.length() - "/move".length());
        String name = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        JsonObject body = readBody(exchange);
        respondResult(exchange, this.context.getSession().movePlayer(name, intOr(body, "offset", 0)));
    }

    private void handleRemovePlayer(HttpExchange exchange) throws IOException {
        String encoded = exchange.getRequestURI().getPath().substring("/api/lobby/players/".length());
        String name = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        respondResult(exchange, this.context.getSession().leaveLobby(name, LobbyPlayer.LOCAL_CLIENT_ID));
    }

    private void handleStartTournament(HttpExchange exchange) throws IOException {
        if (!requireBroker(exchange)) {
            return;
        }
        JsonObject body = readBody(exchange);
        JsonObject rules = body.has("houseRules") ? body.getAsJsonObject("houseRules") : new JsonObject();
        TournamentConfig config = new TournamentConfig(
                stringOr(body, "gameMode", TournamentConfig.MODE_X01),
                intOr(body, "startScore", 301),
                boolOr(body, "doubleIn", false),
                boolOr(body, "doubleOut", false),
                intOr(body, "games", 1),
                intOr(body, "penaltyCostCents", 0),
                new HouseRules(
                        boolOr(rules, "schnapszahlPenalty", true),
                        boolOr(rules, "wallHitPenalty", true),
                        boolOr(rules, "placementPenalty", true)));
        respondResult(exchange, this.context.getSession().startTournament(config));
    }

    private void handleClientJoin(HttpExchange exchange) throws IOException {
        if (!requireBroker(exchange)) {
            return;
        }
        JsonObject body = readBody(exchange);
        respondResult(exchange, this.context.getClientSession().join(
                stringOr(body, "playerName", null),
                intOr(body, "dartboardId", -1)));
    }

    private void handleClientLeave(HttpExchange exchange) throws IOException {
        if (!requireBroker(exchange)) {
            return;
        }
        JsonObject body = readBody(exchange);
        respondResult(exchange, this.context.getClientSession().leave(
                stringOr(body, "playerName", null)));
    }

    /** POST /api/boards/{mac}/connect, .../disconnect und .../reconnect */
    private void handleBoardAction(HttpExchange exchange) throws IOException {
        String rest = exchange.getRequestURI().getPath().substring("/api/boards/".length());
        int slash = rest.lastIndexOf('/');
        if (slash <= 0) {
            respond(exchange, 404, error("Unbekannter Endpunkt"));
            return;
        }
        String mac = URLDecoder.decode(rest.substring(0, slash), StandardCharsets.UTF_8);
        String action = rest.substring(slash + 1);
        if ("connect".equals(action)) {
            if (!requireBroker(exchange)) {
                return;
            }
            JsonObject body = readBody(exchange);
            respondResult(exchange, this.context.getBoardService().connect(mac, intOr(body, "dartboardId", -1)));
        } else if ("disconnect".equals(action)) {
            respondResult(exchange, this.context.getBoardService().disconnect(mac));
        } else if ("reconnect".equals(action)) {
            respondResult(exchange, this.context.getBoardService().reconnect(mac));
        } else {
            respond(exchange, 404, error("Unbekannte Board-Aktion: " + action));
        }
    }

    private boolean requireBroker(HttpExchange exchange) throws IOException {
        if (this.context.isBrokerConfigured()) {
            return true;
        }
        respond(exchange, 409, error("Bitte zuerst den MQTT-Broker konfigurieren"));
        return false;
    }

    private static JsonObject readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
    }

    private static boolean boolOr(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }

    private void respondResult(HttpExchange exchange, LobbyResult result) throws IOException {
        respond(exchange, result.ok() ? 200 : 400, result.ok() ? "{\"ok\":true}" : error(result.error()));
    }

    private static String error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("error", message);
        return json.toString();
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
