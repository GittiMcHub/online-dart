package it.tobaben.dart.infrastructure;

import io.moquette.broker.config.IConfig;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.broker.security.IAuthenticator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * In-process MQTT broker (Moquette) for running the server without an
 * external Mosquitto ("--embedded-broker"). Configured entirely in code, no
 * moquette.conf:
 * <ul>
 *   <li>TCP listener on all interfaces at the MQTT port from {@link MqttConfig}</li>
 *   <li>WebSocket listener (for the webapp/virtualDartboard clients) on
 *       {@code websocketPort}, path "/" like Mosquitto</li>
 *   <li>authentication against exactly the username/password pair from the
 *       {@link MqttConfig} (the same credentials the server itself uses)</li>
 *   <li>no persistence: the server publishes the complete game state after
 *       every throw anyway, so there is nothing worth recovering</li>
 * </ul>
 */
public final class EmbeddedBroker {

    static {
        // silence Moquette's per-packet INFO logging (slf4j-simple reads this
        // before the first logger is created; an explicit user setting wins)
        if (System.getProperty("org.slf4j.simpleLogger.log.io.moquette.broker.metrics") == null) {
            System.setProperty("org.slf4j.simpleLogger.log.io.moquette.broker.metrics", "warn");
        }
    }

    private final Server server = new Server();
    private final MqttConfig mqtt;
    private final int websocketPort;

    public EmbeddedBroker(MqttConfig mqtt, int websocketPort) {
        this.mqtt = mqtt;
        this.websocketPort = websocketPort;
    }

    public void start() throws IOException {
        Properties properties = new Properties();
        properties.setProperty(IConfig.HOST_PROPERTY_NAME, "0.0.0.0");
        properties.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(this.mqtt.port()));
        properties.setProperty(IConfig.WEB_SOCKET_PORT_PROPERTY_NAME, String.valueOf(this.websocketPort));
        properties.setProperty(IConfig.WEB_SOCKET_PATH_PROPERTY_NAME, "/");
        properties.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "false");
        properties.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
        // no phoning home, and no data/.moquette_uuid file in the working dir
        properties.setProperty(IConfig.ENABLE_TELEMETRY_NAME, "false");
        this.server.startServer(new MemoryConfig(properties), List.of(), null,
                singleUserAuthenticator(), null);
    }

    public void stop() {
        this.server.stopServer();
    }

    private IAuthenticator singleUserAuthenticator() {
        return (clientId, username, password) ->
                this.mqtt.username().equals(username)
                        && password != null
                        && this.mqtt.password().equals(new String(password, StandardCharsets.UTF_8));
    }
}
