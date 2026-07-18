package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.board.BoardService;
import it.tobaben.dart.application.client.ClientSession;
import it.tobaben.dart.application.lobby.LobbyService;
import it.tobaben.dart.application.session.ServerSession;
import it.tobaben.dart.application.session.SessionPhase;
import it.tobaben.dart.infrastructure.ble.MqttThrowForwarder;
import it.tobaben.dart.infrastructure.ble.SimpleBleBoardConnection;
import it.tobaben.dart.infrastructure.ble.SimpleBleScannerAdapter;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Runtime state of the long-lived application: broker/MQTT connection plus the
 * ServerSession built on top. The session exists from the start (lobby state
 * survives everything); the broker can be (re)configured from the management
 * UI as long as no tournament is running — the MqttPortBridge retargets the
 * session's outbound ports to the new connection.
 */
public class AppContext {

    private final AppConfig config;
    private final BlockingQueue<DartboardInput> inputQueue = new LinkedBlockingQueue<>();
    private final MqttPortBridge portBridge = new MqttPortBridge();
    private final ServerSession session;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private final ClientSession clientSession = new ClientSession();
    private final BoardService boardService = new BoardService(
            SimpleBleScannerAdapter::new,
            SimpleBleBoardConnection::new,
            new MqttThrowForwarder(this::getMqttAdapter));

    private EmbeddedBroker embeddedBroker;
    private MqttAdapter mqtt;
    private MqttConfig activeMqttConfig;
    private boolean embeddedActive;
    private int embeddedWsPort;
    private MqttLobbyAdapter lobbyAdapter;

    public AppContext(AppConfig config) {
        this.config = config;
        this.embeddedWsPort = config.embeddedBrokerWsPort();
        this.session = new ServerSession(this.inputQueue, new LobbyService(), this.portBridge, this.portBridge);
        this.session.addChangeListener(this::fireChange);
        this.clientSession.addChangeListener(this::fireChange);
        this.boardService.addChangeListener(this::fireChange);
    }

    public AppConfig getConfig() {
        return this.config;
    }

    public ServerSession getSession() {
        return this.session;
    }

    public void addChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    /**
     * Starts the embedded broker (or targets an external one) and connects the
     * MQTT adapter. Reconfiguration is allowed while no tournament runs.
     *
     * @return German error message, or null on success
     */
    public synchronized String configureBroker(boolean embedded, MqttConfig mqttConfig, int wsPort) {
        if (this.session.getPhase() == SessionPhase.RUNNING) {
            return "Turnier läuft bereits – Broker kann jetzt nicht geändert werden";
        }
        mqttConfig = effectiveMqttConfig(this.config.mode(), this.clientSession.getClientId(), mqttConfig);
        try {
            if (this.mqtt != null) {
                this.mqtt.disconnect();
                this.mqtt = null;
            }
            if (this.embeddedBroker != null) {
                this.embeddedBroker.stop();
                this.embeddedBroker = null;
            }
            if (embedded) {
                EmbeddedBroker starting = new EmbeddedBroker(mqttConfig, wsPort);
                starting.start();
                this.embeddedBroker = starting;
                System.out.println("[APP] Eingebetteter MQTT-Broker gestartet: TCP-Port "
                        + mqttConfig.port() + ", WebSocket-Port " + wsPort
                        + ", Benutzer " + mqttConfig.username());
            }
            MqttAdapter adapter = new MqttAdapter(mqttConfig, this.inputQueue);
            if (this.config.mode().actsAsClient()) {
                // if this client dies, the server removes its lobby players
                adapter.setLastWill(MqttLobbyAdapter.LEAVE_TOPIC,
                        MqttLobbyClient.lastWillPayload(this.clientSession.getClientId()));
            }
            adapter.connect();
            this.mqtt = adapter;
            this.activeMqttConfig = mqttConfig;
            this.embeddedActive = embedded;
            this.embeddedWsPort = wsPort;
            this.portBridge.retarget(adapter);

            if (this.config.mode().actsAsServer()) {
                if (this.lobbyAdapter != null) {
                    this.lobbyAdapter.detach();
                }
                this.lobbyAdapter = new MqttLobbyAdapter(adapter, this.session, this.config.serverName());
                this.lobbyAdapter.start();
            }
            if (this.config.mode().actsAsClient()) {
                new MqttLobbyClient(adapter, this.clientSession).start();
            }
            fireChange();
            return null;
        } catch (IOException | MqttException | RuntimeException e) {
            return "Broker-Start/Verbindung fehlgeschlagen: " + e.getMessage();
        }
    }

    /**
     * A pure client must not connect with the default id "server": the broker
     * would treat it as the server's session and kick whichever of the two
     * connected first. Only an explicitly configured id is kept.
     */
    static MqttConfig effectiveMqttConfig(AppMode mode, String clientSessionId, MqttConfig mqttConfig) {
        if (!mode.actsAsServer() && MqttConfig.DEFAULT_CLIENT_ID.equals(mqttConfig.clientId())) {
            return mqttConfig.withClientId("client-" + clientSessionId.substring(0, 8));
        }
        return mqttConfig;
    }

    /** Applies the CLI-provided broker settings immediately (--auto-broker). */
    public String configureBrokerFromConfig() {
        return configureBroker(this.config.embeddedBroker(), this.config.mqtt(), this.config.embeddedBrokerWsPort());
    }

    public synchronized boolean isBrokerConfigured() {
        return this.mqtt != null;
    }

    public synchronized boolean isEmbeddedBrokerActive() {
        return this.embeddedBroker != null && this.embeddedBroker.isRunning();
    }

    public synchronized boolean isMqttConnected() {
        return this.mqtt != null && this.mqtt.isConnected();
    }

    public synchronized MqttConfig getActiveMqttConfig() {
        return this.activeMqttConfig;
    }

    public synchronized boolean isEmbeddedActive() {
        return this.embeddedActive;
    }

    public synchronized int getEmbeddedWsPort() {
        return this.embeddedWsPort;
    }

    public String getLastGameJson() {
        return this.portBridge.getLastJson();
    }

    public ClientSession getClientSession() {
        return this.clientSession;
    }

    public BoardService getBoardService() {
        return this.boardService;
    }

    synchronized MqttAdapter getMqttAdapter() {
        return this.mqtt;
    }

    public synchronized void shutdown() {
        this.boardService.shutdown();
        if (this.lobbyAdapter != null) {
            this.lobbyAdapter.shutdown(); // retained "lobby closed" state
        }
        try {
            if (this.mqtt != null) {
                this.mqtt.disconnect();
            }
        } catch (MqttException e) {
            System.err.println("[APP] MQTT-Disconnect fehlgeschlagen: " + e.getMessage());
        }
        if (this.embeddedBroker != null) {
            this.embeddedBroker.stop();
        }
    }

    private void fireChange() {
        for (Runnable listener : this.changeListeners) {
            listener.run();
        }
    }
}
