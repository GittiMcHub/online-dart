package it.tobaben.dart.infrastructure.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.tobaben.dart.infrastructure.AppConfig;
import it.tobaben.dart.infrastructure.AppContext;
import it.tobaben.dart.infrastructure.AppMode;
import it.tobaben.dart.infrastructure.MqttConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class WebServerTest {

    // fixed free-ish test port; keep away from 8420 to not clash with a running app
    static final int PORT = 18420;

    AppContext context;
    WebServer webServer;
    HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws IOException {
        AppConfig config = new AppConfig(AppMode.SERVER, PORT, "Test-Server", false, true, 18083,
                new MqttConfig("127.0.0.1", 11883, "dartboard", "smartness", "server", 0));
        context = new AppContext(config);
        webServer = new WebServer(context);
        webServer.start();
    }

    @AfterEach
    void tearDown() {
        webServer.stop();
        context.shutdown();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + path))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void stateEndpointReportsUnconfiguredBroker() throws Exception {
        HttpResponse<String> response = get("/api/state");
        assertEquals(200, response.statusCode());
        JsonObject state = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals("SERVER", state.get("mode").getAsString());
        assertEquals("IDLE", state.get("phase").getAsString());
        assertFalse(state.getAsJsonObject("broker").get("configured").getAsBoolean());
        assertEquals("UNDEFINED", state.getAsJsonObject("game").get("gameState").getAsString());
    }

    @Test
    void servesManagementUi() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Dart Server"));
    }

    @Test
    void servesDisplay() throws Exception {
        HttpResponse<String> response = get("/display/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("config.js"));
    }

    @Test
    void configJsWithoutBrokerSaysUnconfigured() throws Exception {
        HttpResponse<String> response = get("/config.js");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("configured: false"));
        assertTrue(response.body().contains("location.hostname"));
    }

    @Test
    void tournamentStartWithoutBrokerIsRejected() throws Exception {
        HttpResponse<String> response = post("/api/tournament/start", "{}");
        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("Broker"));
    }

    @Test
    void lobbyLifecycleOverRest() throws Exception {
        assertEquals(200, post("/api/lobby/open", "").statusCode());
        // adding players requires a broker (throws would go nowhere otherwise)
        assertEquals(409, post("/api/lobby/players", "{\"name\":\"Alice\",\"dartboardId\":1}").statusCode());
        assertEquals(200, post("/api/lobby/close", "").statusCode());
    }

    @Test
    void unknownEndpointIs404() throws Exception {
        assertEquals(404, get("/api/nope").statusCode());
    }
}
