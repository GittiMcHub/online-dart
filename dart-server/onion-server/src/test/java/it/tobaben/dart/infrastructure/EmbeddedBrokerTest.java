package it.tobaben.dart.infrastructure;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: real Moquette broker on random free ports, real paho
 * clients over TCP and WebSocket.
 */
class EmbeddedBrokerTest {

    private EmbeddedBroker broker;
    private int tcpPort;
    private int wsPort;

    @BeforeEach
    void startBroker() throws IOException {
        tcpPort = freePort();
        wsPort = freePort();
        MqttConfig config = new MqttConfig("127.0.0.1", tcpPort, "dartboard", "smartness", "server", 1);
        broker = new EmbeddedBroker(config, wsPort);
        broker.start();
    }

    @AfterEach
    void stopBroker() {
        broker.stop();
    }

    @Test
    void routesMessagesBetweenAuthenticatedClients() throws Exception {
        MqttClient subscriber = connect("tcp://127.0.0.1:" + tcpPort, "sub", "smartness");
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();
        subscriber.subscribe("dartboard/1", 1, (topic, message) -> {
            payload.set(new String(message.getPayload()));
            received.countDown();
        });

        MqttClient publisher = connect("tcp://127.0.0.1:" + tcpPort, "pub", "smartness");
        publisher.publish("dartboard/1", "320".getBytes(), 1, false);

        assertTrue(received.await(5, TimeUnit.SECONDS), "message not routed");
        assertEquals("320", payload.get());
        publisher.disconnect();
        subscriber.disconnect();
    }

    // gameUpdates grow with players (statistik per Spieler, finished players are
    // repeated in spielerPlatzierung) — Moquette's 8092-byte default dropped them
    private static final String LARGE_GAME_UPDATE = "x".repeat(20_000);

    @Test
    void routesGameUpdatesLargerThanMoquettesDefaultLimit() throws Exception {
        assertLargeMessageRouted("tcp://127.0.0.1:" + tcpPort);
    }

    @Test
    void deliversLargeGameUpdatesToWebSocketDisplays() throws Exception {
        assertLargeMessageRouted("ws://127.0.0.1:" + wsPort + "/");
    }

    private void assertLargeMessageRouted(String subscriberUrl) throws Exception {
        MqttClient subscriber = connect(subscriberUrl, "display", "smartness");
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();
        subscriber.subscribe("status/gameUpdate", 1, (topic, message) -> {
            payload.set(new String(message.getPayload()));
            received.countDown();
        });

        MqttClient server = connect("tcp://127.0.0.1:" + tcpPort, "server", "smartness");
        server.publish("status/gameUpdate", LARGE_GAME_UPDATE.getBytes(), 1, false);

        assertTrue(received.await(5, TimeUnit.SECONDS), "large message not routed");
        assertEquals(LARGE_GAME_UPDATE, payload.get());
        server.disconnect();
        subscriber.disconnect();
    }

    @Test
    void acceptsWebSocketClientsLikeMosquitto() throws Exception {
        MqttClient wsClient = connect("ws://127.0.0.1:" + wsPort + "/", "ws", "smartness");
        assertTrue(wsClient.isConnected());
        wsClient.disconnect();
    }

    @Test
    void rejectsWrongCredentials() {
        assertThrows(MqttException.class,
                () -> connect("tcp://127.0.0.1:" + tcpPort, "bad", "falsch"));
    }

    private static MqttClient connect(String url, String clientId, String password) throws MqttException {
        MqttClient client = new MqttClient(url, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName("dartboard");
        options.setPassword(password.toCharArray());
        client.connect(options);
        return client;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
