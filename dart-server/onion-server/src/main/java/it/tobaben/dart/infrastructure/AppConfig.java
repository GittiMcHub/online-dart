package it.tobaben.dart.infrastructure;

/**
 * Resolved configuration of the long-lived web mode (no --player given).
 * The MQTT settings are the defaults offered by the management UI; with
 * --auto-broker they are applied immediately at startup.
 */
public record AppConfig(
        AppMode mode,
        int webPort,
        String serverName,
        boolean autoBroker,
        boolean embeddedBroker,
        int embeddedBrokerWsPort,
        MqttConfig mqtt
) {
}
