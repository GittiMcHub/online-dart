package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.client.ClientSession;
import it.tobaben.dart.application.lobby.LobbyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full lobby round-trip over a real embedded broker: a SERVER-mode AppContext
 * and a genuine remote client (own MqttAdapter + ClientSession +
 * MqttLobbyClient) talking over MQTT on test ports.
 */
class MqttLobbyIntegrationTest {

    static final int TCP_PORT = 11884;
    static final int WS_PORT = 18085;

    AppContext server;
    MqttAdapter clientMqtt;
    ClientSession clientSession;

    @BeforeEach
    void setUp() throws Exception {
        MqttConfig serverMqtt = new MqttConfig("127.0.0.1", TCP_PORT, "dartboard", "smartness", "server-it", 0);
        server = new AppContext(new AppConfig(AppMode.SERVER, 18421, "IT-Server", false, true, WS_PORT, serverMqtt));
        assertNull(server.configureBroker(true, serverMqtt, WS_PORT));

        clientSession = new ClientSession();
        MqttConfig remote = new MqttConfig("127.0.0.1", TCP_PORT, "dartboard", "smartness", "client-it", 0);
        clientMqtt = new MqttAdapter(remote, new LinkedBlockingQueue<DartboardInput>());
        clientMqtt.setLastWill(MqttLobbyAdapter.LEAVE_TOPIC, MqttLobbyClient.lastWillPayload(clientSession.getClientId()));
        clientMqtt.connect();
        new MqttLobbyClient(clientMqtt, clientSession).start();
        await(() -> clientMqtt.isConnected());
    }

    @AfterEach
    void tearDown() throws Exception {
        clientMqtt.disconnect();
        server.shutdown();
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timeout beim Warten auf Bedingung");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void remoteClientJoinsAndLeavesLobby() {
        server.getSession().openLobby();
        await(() -> clientSession.getRemoteLobby() != null
                && "LOBBY".equals(clientSession.getRemoteLobby().phase()));

        LobbyResult join = clientSession.join("Remote-Alice", 3);
        assertTrue(join.ok(), () -> "join fehlgeschlagen: " + join.error());
        assertEquals(1, server.getSession().getLobby().getPlayers().size());
        assertEquals("Remote-Alice", server.getSession().getLobby().getPlayers().get(0).name());

        // the broadcast state reaches the client including its own player
        await(() -> clientSession.getRemoteLobby().players().size() == 1);

        LobbyResult leave = clientSession.leave("Remote-Alice");
        assertTrue(leave.ok());
        await(() -> server.getSession().getLobby().isEmpty());
    }

    @Test
    void duplicateNameIsRejectedWithGermanError() {
        server.getSession().openLobby();
        server.getSession().joinLobby("Alice", 1, "local");
        await(() -> clientSession.getRemoteLobby() != null
                && "LOBBY".equals(clientSession.getRemoteLobby().phase()));

        LobbyResult result = clientSession.join("Alice", 2);
        assertFalse(result.ok());
        assertTrue(result.error().contains("bereits vergeben"));
    }

    @Test
    void dartboardConflictAcrossClientsIsRejected() {
        server.getSession().openLobby();
        server.getSession().joinLobby("Host-Spieler", 1, "local");
        await(() -> clientSession.getRemoteLobby() != null
                && "LOBBY".equals(clientSession.getRemoteLobby().phase()));

        LobbyResult result = clientSession.join("Remote-Bob", 1);
        assertFalse(result.ok());
        assertTrue(result.error().contains("anderen Client"));
    }

    @Test
    void joinWithClosedLobbyIsRejected() {
        await(() -> clientSession.getRemoteLobby() != null);
        LobbyResult result = clientSession.join("Zu-Frueh", 1);
        assertFalse(result.ok());
        assertTrue(result.error().contains("nicht geöffnet"));
    }

    @Test
    void retainedStateReachesLateJoiner() throws Exception {
        server.getSession().openLobby();
        server.getSession().joinLobby("Alice", 1, "local");

        ClientSession lateSession = new ClientSession();
        MqttConfig late = new MqttConfig("127.0.0.1", TCP_PORT, "dartboard", "smartness", "late-it", 0);
        MqttAdapter lateMqtt = new MqttAdapter(late, new LinkedBlockingQueue<DartboardInput>());
        lateMqtt.connect();
        new MqttLobbyClient(lateMqtt, lateSession).start();
        try {
            await(() -> lateSession.getRemoteLobby() != null
                    && lateSession.getRemoteLobby().players().size() == 1);
            assertEquals("IT-Server", lateSession.getRemoteLobby().serverName());
        } finally {
            lateMqtt.disconnect();
        }
    }
}
