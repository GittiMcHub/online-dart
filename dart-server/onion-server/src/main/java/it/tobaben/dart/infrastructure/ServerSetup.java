package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.common.Player;
import org.apache.commons.cli.*;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Builds the ServerConfig from CLI flags (see TODO.md Phase 4), the
 * anthrax-compatible mqttbroker.conf and interactive stdin prompts for
 * everything not given as a flag. MQTT settings are never prompted: they come
 * from the conf file, individual flags override it; with --mqtt-host,
 * --mqtt-user and --mqtt-password all given the file is not needed. With
 * --embedded-broker the conf file is optional too (defaults: 127.0.0.1:1883,
 * dartboard/smartness).
 */
public final class ServerSetup {

    private ServerSetup() {
    }

    public static Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder().longOpt("mqtt-host").hasArg().argName("host").desc("Hostname/IP des MQTT Brokers").build());
        options.addOption(Option.builder().longOpt("mqtt-port").hasArg().argName("port").desc("Port des MQTT Brokers (Default 1883)").build());
        options.addOption(Option.builder().longOpt("mqtt-user").hasArg().argName("user").desc("MQTT Benutzername").build());
        options.addOption(Option.builder().longOpt("mqtt-password").hasArg().argName("pass").desc("MQTT Passwort").build());
        options.addOption(Option.builder().longOpt("mqtt-client-id").hasArg().argName("id").desc("MQTT Client-ID (Default 'server')").build());
        options.addOption(Option.builder().longOpt("mqtt-qos").hasArg().argName("qos").desc("QoS für die dartboard/# Subscription (Default 0)").build());
        options.addOption(Option.builder().longOpt("mqtt-config").hasArg().argName("pfad").desc("Pfad zur mqttbroker.conf (Default ./mqttbroker.conf)").build());
        options.addOption(Option.builder().longOpt("embedded-broker").desc("Eingebetteten MQTT-Broker (Moquette) starten statt einen externen zu nutzen; ohne weitere Angaben mit dartboard/smartness auf Port 1883").build());
        options.addOption(Option.builder().longOpt("embedded-broker-ws-port").hasArg().argName("port").desc("WebSocket-Port des eingebetteten Brokers für Web-Clients (Default 8083)").build());

        options.addOption(Option.builder().longOpt("game-mode").hasArg().argName("modus").desc("Spielmodus: x01 oder cricket (Default x01)").build());
        options.addOption(Option.builder().longOpt("start-score").hasArg().argName("punkte").desc("Startpunkte für x01, z.B. 301 oder 501 (Default 301)").build());
        options.addOption(Option.builder().longOpt("double-in").desc("Double-In aktivieren (nur x01)").build());
        options.addOption(Option.builder().longOpt("double-out").desc("Double-Out aktivieren (nur x01)").build());
        options.addOption(Option.builder().longOpt("games").hasArg().argName("anzahl").desc("Anzahl Spiele des Turniers (Default 1)").build());
        options.addOption(Option.builder().longOpt("penalty-cost").hasArg().argName("cent").desc("Kosten pro Strafpunkt in Cent (Default 0)").build());
        options.addOption(Option.builder().longOpt("player").hasArg().argName("name:boardId").desc("Spieler, wiederholbar: --player Alice:1 --player Bob:1").build());

        options.addOption(Option.builder().longOpt("no-schnapszahl-penalty").desc("Schnapszahl-Strafpunkte deaktivieren").build());
        options.addOption(Option.builder().longOpt("no-wallhit-penalty").desc("Wandtreffer-Strafpunkte deaktivieren").build());
        options.addOption(Option.builder().longOpt("no-placement-penalty").desc("Platzierungs-Strafpunkte deaktivieren").build());

        // long-lived web mode (without --player); see AppSetup
        options.addOption(Option.builder().longOpt("mode").hasArg().argName("modus").desc("Betriebsmodus: server, client oder combined (Default combined)").build());
        options.addOption(Option.builder().longOpt("web-port").hasArg().argName("port").desc("Port des Web-Interfaces (Default 8420)").build());
        options.addOption(Option.builder().longOpt("server-name").hasArg().argName("name").desc("Anzeigename des Servers in der Lobby").build());
        options.addOption(Option.builder().longOpt("auto-broker").desc("MQTT-Broker beim Start sofort gemäß Flags starten/verbinden (headless, ohne Klick im Web-UI)").build());

        options.addOption(Option.builder().longOpt("help").desc("Diese Hilfe anzeigen").build());
        return options;
    }

    /** @return empty if --help was requested (help already printed) */
    public static Optional<ServerConfig> fromArgs(String[] args, InputStream in, PrintStream out) throws ParseException {
        Options options = buildOptions();
        CommandLine cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption("help")) {
            new HelpFormatter().printHelp("onion-server", options, true);
            return Optional.empty();
        }
        Scanner scanner = new Scanner(in);

        MqttConfig mqtt = resolveMqtt(cmd);

        String gameMode = cmd.hasOption("game-mode")
                ? cmd.getOptionValue("game-mode")
                : prompt(scanner, out, "Spielmodus (x01/cricket)", ServerConfig.MODE_X01);
        gameMode = gameMode.toLowerCase();
        if (!ServerConfig.MODE_X01.equals(gameMode) && !ServerConfig.MODE_CRICKET.equals(gameMode)) {
            throw new IllegalArgumentException("Unbekannter Spielmodus: " + gameMode);
        }

        int startScore = 301;
        if (ServerConfig.MODE_X01.equals(gameMode)) {
            startScore = parsePositive("start-score", cmd.hasOption("start-score")
                    ? cmd.getOptionValue("start-score")
                    : prompt(scanner, out, "Startpunkte (z.B. 301 oder 501)", "301"));
        }

        int games = parsePositive("games", cmd.hasOption("games")
                ? cmd.getOptionValue("games")
                : prompt(scanner, out, "Anzahl Spiele des Turniers", "1"));

        List<RegisteredPlayer> players = cmd.hasOption("player")
                ? parsePlayers(cmd.getOptionValues("player"))
                : promptPlayers(scanner, out);

        int penaltyCost = parseNonNegative("penalty-cost", cmd.hasOption("penalty-cost")
                ? cmd.getOptionValue("penalty-cost")
                : prompt(scanner, out, "Kosten pro Strafpunkt in Cent", "0"));

        HouseRules houseRules = new HouseRules(
                !cmd.hasOption("no-schnapszahl-penalty"),
                !cmd.hasOption("no-wallhit-penalty"),
                !cmd.hasOption("no-placement-penalty"));

        int embeddedWsPort = parsePositive("embedded-broker-ws-port",
                cmd.getOptionValue("embedded-broker-ws-port", "8083"));

        return Optional.of(new ServerConfig(mqtt,
                cmd.hasOption("embedded-broker"), embeddedWsPort,
                gameMode, startScore,
                cmd.hasOption("double-in"), cmd.hasOption("double-out"),
                games, penaltyCost, players, houseRules));
    }

    static MqttConfig resolveMqtt(CommandLine cmd) {
        boolean embedded = cmd.hasOption("embedded-broker");
        boolean fullFlags = cmd.hasOption("mqtt-host") && cmd.hasOption("mqtt-user") && cmd.hasOption("mqtt-password");
        // With the embedded broker the server is self-contained: the conf file
        // is only read when --mqtt-config is passed explicitly, otherwise the
        // repo-wide defaults apply. The broker listens on the resolved port and
        // accepts exactly the resolved username/password.
        boolean useConfFile = !fullFlags && (!embedded || cmd.hasOption("mqtt-config"));
        MqttConfig base = useConfFile
                ? MqttConfig.fromFile(Paths.get(cmd.getOptionValue("mqtt-config", MqttConfig.DEFAULT_FILE)))
                : null;
        String defaultHost = embedded ? "127.0.0.1" : null;
        String defaultUser = embedded ? "dartboard" : null;
        String defaultPassword = embedded ? "smartness" : null;
        return new MqttConfig(
                cmd.getOptionValue("mqtt-host", base == null ? defaultHost : base.host()),
                Integer.parseInt(cmd.getOptionValue("mqtt-port", base == null ? "1883" : String.valueOf(base.port()))),
                cmd.getOptionValue("mqtt-user", base == null ? defaultUser : base.username()),
                cmd.getOptionValue("mqtt-password", base == null ? defaultPassword : base.password()),
                cmd.getOptionValue("mqtt-client-id", base == null ? MqttConfig.DEFAULT_CLIENT_ID : base.clientId()),
                Integer.parseInt(cmd.getOptionValue("mqtt-qos", base == null ? "0" : String.valueOf(base.qos())))
        );
    }

    static List<RegisteredPlayer> parsePlayers(String[] values) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (String value : values) {
            int separator = value.lastIndexOf(':');
            if (separator <= 0 || separator == value.length() - 1) {
                throw new IllegalArgumentException("Ungültiges --player Format (erwartet name:boardId): " + value);
            }
            String name = value.substring(0, separator);
            int boardId = parseNonNegative("player boardId", value.substring(separator + 1));
            players.add(new RegisteredPlayer(players.size(), new Player(name), boardId));
        }
        if (players.isEmpty()) {
            throw new IllegalArgumentException("Mindestens ein Spieler wird benötigt");
        }
        return players;
    }

    private static List<RegisteredPlayer> promptPlayers(Scanner scanner, PrintStream out) {
        int count = parsePositive("Anzahl Spieler", prompt(scanner, out, "Anzahl Spieler", null));
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = prompt(scanner, out, "Name für Spieler " + (i + 1), null);
            int boardId = parseNonNegative("Dartboard-ID",
                    prompt(scanner, out, "Dartboard-ID für Spieler " + (i + 1), null));
            players.add(new RegisteredPlayer(i, new Player(name), boardId));
        }
        return players;
    }

    private static String prompt(Scanner scanner, PrintStream out, String text, String defaultValue) {
        out.println("[SETUP] " + text + (defaultValue == null ? "" : " (Enter = " + defaultValue + ")") + ": ");
        String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (line.isEmpty()) {
            if (defaultValue == null) {
                throw new IllegalArgumentException("Eingabe für '" + text + "' fehlt");
            }
            return defaultValue;
        }
        return line;
    }

    private static int parsePositive(String name, String value) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " muss > 0 sein: " + value);
        }
        return parsed;
    }

    private static int parseNonNegative(String name, String value) {
        int parsed = Integer.parseInt(value.trim());
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " darf nicht negativ sein: " + value);
        }
        return parsed;
    }
}
