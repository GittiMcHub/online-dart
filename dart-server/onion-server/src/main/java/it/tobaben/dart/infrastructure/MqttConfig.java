package it.tobaben.dart.infrastructure;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * MQTT connection settings. Loads the anthrax-compatible mqttbroker.conf
 * (java.util.Properties format with the keys mqtt_broker_ip, mqtt_broker_port,
 * mqtt_username, mqtt_password, mqtt_qos) so existing config files keep working.
 */
public record MqttConfig(String host, int port, String username, String password, String clientId, int qos) {

    public static final String DEFAULT_FILE = System.getProperty("user.dir") + File.separator + "mqttbroker.conf";
    public static final String DEFAULT_CLIENT_ID = "server";

    public String brokerUrl() {
        return "tcp://" + this.host + ":" + this.port;
    }

    public static MqttConfig fromFile(Path path) {
        try {
            Properties properties = new Properties();
            properties.load(Files.newBufferedReader(path));
            return new MqttConfig(
                    properties.getProperty("mqtt_broker_ip"),
                    Integer.parseInt(properties.getProperty("mqtt_broker_port", "1883")),
                    properties.getProperty("mqtt_username"),
                    properties.getProperty("mqtt_password"),
                    properties.getProperty("mqtt_client_id", DEFAULT_CLIENT_ID),
                    Integer.parseInt(properties.getProperty("mqtt_qos", "0"))
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot read MQTT config file: " + path, e);
        }
    }
}
