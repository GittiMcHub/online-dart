package it.tobaben.dart.infrastructure;

import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerSetupTest {

    @TempDir
    Path tempDir;

    private Path writeConf() throws Exception {
        Path conf = tempDir.resolve("mqttbroker.conf");
        Files.writeString(conf, """
                mqtt_broker_ip:192.168.0.5
                mqtt_broker_port:1884
                mqtt_username:dartboard
                mqtt_password:smartness
                mqtt_qos:2
                """);
        return conf;
    }

    private static ServerConfig setup(String[] args, String stdin) throws ParseException {
        InputStream in = new ByteArrayInputStream(stdin.getBytes());
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        return ServerSetup.fromArgs(args, in, out).orElseThrow();
    }

    @Test
    void fullFlagsNeedNoConfFileAndNoPrompts() throws ParseException {
        ServerConfig config = setup(new String[]{
                "--mqtt-host", "10.0.0.1", "--mqtt-user", "u", "--mqtt-password", "p",
                "--mqtt-qos", "1",
                "--game-mode", "x01", "--start-score", "501", "--double-out",
                "--games", "3", "--penalty-cost", "25",
                "--player", "Alice:1", "--player", "Bob:2",
                "--no-wallhit-penalty"
        }, "");

        assertEquals("tcp://10.0.0.1:1883", config.mqtt().brokerUrl());
        assertEquals("u", config.mqtt().username());
        assertEquals(1, config.mqtt().qos());
        assertEquals("server", config.mqtt().clientId());
        assertEquals("x01", config.gameMode());
        assertEquals(501, config.startScore());
        assertFalse(config.doubleIn());
        assertTrue(config.doubleOut());
        assertEquals(3, config.games());
        assertEquals(25, config.penaltyCostCents());
        assertFalse(config.embeddedBroker());
        assertEquals(2, config.players().size());
        assertEquals("Alice", config.players().get(0).getName());
        assertEquals(1, config.players().get(0).dartboardId());
        assertEquals(0, config.players().get(0).id());
        assertEquals("Bob", config.players().get(1).getName());
        assertTrue(config.houseRules().schnapszahlPenalty());
        assertFalse(config.houseRules().wallHitPenalty());
        assertTrue(config.houseRules().placementPenalty());
    }

    @Test
    void confFileProvidesMqttAndFlagsOverrideIt() throws Exception {
        Path conf = writeConf();
        ServerConfig config = setup(new String[]{
                "--mqtt-config", conf.toString(),
                "--mqtt-host", "10.1.1.1", // override
                "--game-mode", "cricket", "--games", "1", "--penalty-cost", "0",
                "--player", "Alice:1"
        }, "");

        assertEquals("tcp://10.1.1.1:1884", config.mqtt().brokerUrl()); // host flag, port from file
        assertEquals("dartboard", config.mqtt().username());
        assertEquals("smartness", config.mqtt().password());
        assertEquals(2, config.mqtt().qos());
        assertEquals("cricket", config.gameMode());
    }

    @Test
    void missingValuesArePromptedInteractively() throws Exception {
        Path conf = writeConf();
        // prompts in order: game mode, start score, games, player count,
        // name 1, board 1, name 2, board 2, penalty cost
        String stdin = String.join("\n",
                "",       // game mode -> default x01
                "501",    // start score
                "2",      // games
                "2",      // player count
                "Alice", "1",
                "Bob", "2",
                "50"      // penalty cost
        ) + "\n";
        ServerConfig config = setup(new String[]{"--mqtt-config", conf.toString()}, stdin);

        assertEquals("x01", config.gameMode());
        assertEquals(501, config.startScore());
        assertEquals(2, config.games());
        assertEquals(2, config.players().size());
        assertEquals("Bob", config.players().get(1).getName());
        assertEquals(2, config.players().get(1).dartboardId());
        assertEquals(50, config.penaltyCostCents());
    }

    @Test
    void cricketSkipsStartScorePrompt() throws Exception {
        Path conf = writeConf();
        String stdin = String.join("\n",
                "cricket", // game mode
                "1",       // games
                "1",       // player count
                "Alice", "1",
                "0"        // penalty cost
        ) + "\n";
        ServerConfig config = setup(new String[]{"--mqtt-config", conf.toString()}, stdin);
        assertEquals("cricket", config.gameMode());
        assertNotNull(config.gameFactory().get());
    }

    @Test
    void embeddedBrokerNeedsNoConfFileAndUsesRepoDefaults() throws ParseException {
        ServerConfig config = setup(new String[]{
                "--embedded-broker",
                "--game-mode", "x01", "--start-score", "301", "--games", "1",
                "--penalty-cost", "0", "--player", "Alice:1"
        }, "");

        assertTrue(config.embeddedBroker());
        assertEquals(8083, config.embeddedBrokerWsPort());
        assertEquals("tcp://127.0.0.1:1883", config.mqtt().brokerUrl());
        assertEquals("dartboard", config.mqtt().username());
        assertEquals("smartness", config.mqtt().password());
        assertEquals(0, config.mqtt().qos());
    }

    @Test
    void embeddedBrokerHonorsExplicitConfFileAndWsPortFlag() throws Exception {
        Path conf = writeConf();
        ServerConfig config = setup(new String[]{
                "--embedded-broker", "--embedded-broker-ws-port", "9001",
                "--mqtt-config", conf.toString(),
                "--game-mode", "x01", "--start-score", "301", "--games", "1",
                "--penalty-cost", "0", "--player", "Alice:1"
        }, "");

        assertTrue(config.embeddedBroker());
        assertEquals(9001, config.embeddedBrokerWsPort());
        assertEquals(1884, config.mqtt().port());
        assertEquals(2, config.mqtt().qos());
    }

    @Test
    void invalidInputsAreRejected() throws Exception {
        Path conf = writeConf();
        assertThrows(IllegalArgumentException.class, () -> setup(new String[]{
                "--mqtt-config", conf.toString(), "--game-mode", "shanghai",
                "--player", "Alice:1"}, ""));
        assertThrows(IllegalArgumentException.class, () -> setup(new String[]{
                "--mqtt-config", conf.toString(), "--player", "AliceOhneBoard"}, ""));
        assertThrows(IllegalArgumentException.class, () -> setup(new String[]{
                "--mqtt-config", conf.toString(), "--games", "0", "--player", "Alice:1"}, ""));
        assertThrows(IllegalArgumentException.class, () -> setup(new String[]{
                "--mqtt-config", tempDir.resolve("missing.conf").toString(), "--player", "Alice:1"}, ""));
    }

    @Test
    void helpReturnsEmpty() throws ParseException {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        assertTrue(ServerSetup.fromArgs(new String[]{"--help"}, in, out).isEmpty());
    }
}
