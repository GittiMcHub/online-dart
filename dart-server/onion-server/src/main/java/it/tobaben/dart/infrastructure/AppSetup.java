package it.tobaben.dart.infrastructure;

import org.apache.commons.cli.CommandLine;

/**
 * Builds the AppConfig for the long-lived web mode from the already parsed
 * command line (options come from ServerSetup.buildOptions(), so legacy and
 * web mode share one flag set and one --help).
 */
public final class AppSetup {

    public static final int DEFAULT_WEB_PORT = 8420;

    private AppSetup() {
    }

    public static AppConfig fromCommandLine(CommandLine cmd) {
        AppMode mode = AppMode.parse(cmd.getOptionValue("mode", "combined"));
        int webPort = Integer.parseInt(cmd.getOptionValue("web-port", String.valueOf(DEFAULT_WEB_PORT)));
        if (webPort <= 0 || webPort > 65535) {
            throw new IllegalArgumentException("Ungültiger Web-Port: " + webPort);
        }
        String serverName = cmd.getOptionValue("server-name", "Dart-Server");

        // Without --embedded-broker/--mqtt-* flags the UI offers the embedded
        // broker with repo defaults; resolveMqtt falls back to the conf file
        // only when explicitly requested or when an external broker is targeted.
        boolean embedded = cmd.hasOption("embedded-broker")
                || (!cmd.hasOption("mqtt-host") && !cmd.hasOption("mqtt-config"));
        MqttConfig mqtt = embedded && !cmd.hasOption("embedded-broker")
                ? new MqttConfig("127.0.0.1", 1883, "dartboard", "smartness", MqttConfig.DEFAULT_CLIENT_ID, 0)
                : ServerSetup.resolveMqtt(cmd);
        int embeddedWsPort = Integer.parseInt(cmd.getOptionValue("embedded-broker-ws-port", "8083"));

        return new AppConfig(mode, webPort, serverName, cmd.hasOption("auto-broker"),
                embedded, embeddedWsPort, mqtt);
    }
}
