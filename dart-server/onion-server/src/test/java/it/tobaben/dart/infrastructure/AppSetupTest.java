package it.tobaben.dart.infrastructure;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppSetupTest {

    private CommandLine parse(String... args) throws ParseException {
        return new DefaultParser().parse(ServerSetup.buildOptions(), args);
    }

    @Test
    void defaultsToCombinedModeAndEmbeddedBroker() throws ParseException {
        AppConfig config = AppSetup.fromCommandLine(parse());
        assertEquals(AppMode.COMBINED, config.mode());
        assertEquals(AppSetup.DEFAULT_WEB_PORT, config.webPort());
        assertTrue(config.embeddedBroker());
        assertFalse(config.autoBroker());
        assertEquals("127.0.0.1", config.mqtt().host());
        assertEquals(1883, config.mqtt().port());
        assertEquals("dartboard", config.mqtt().username());
        assertEquals("smartness", config.mqtt().password());
        assertEquals(8083, config.embeddedBrokerWsPort());
    }

    @Test
    void parsesModeAndWebPort() throws ParseException {
        AppConfig config = AppSetup.fromCommandLine(parse("--mode", "server", "--web-port", "9000"));
        assertEquals(AppMode.SERVER, config.mode());
        assertEquals(9000, config.webPort());
    }

    @Test
    void rejectsUnknownMode() throws ParseException {
        CommandLine cmd = parse("--mode", "quatsch");
        assertThrows(IllegalArgumentException.class, () -> AppSetup.fromCommandLine(cmd));
    }

    @Test
    void rejectsInvalidWebPort() throws ParseException {
        CommandLine cmd = parse("--web-port", "0");
        assertThrows(IllegalArgumentException.class, () -> AppSetup.fromCommandLine(cmd));
    }

    @Test
    void externalBrokerFromMqttFlags() throws ParseException {
        AppConfig config = AppSetup.fromCommandLine(parse(
                "--mqtt-host", "10.0.10.1", "--mqtt-user", "dartboard", "--mqtt-password", "geheim"));
        assertFalse(config.embeddedBroker());
        assertEquals("10.0.10.1", config.mqtt().host());
        assertEquals("geheim", config.mqtt().password());
    }

    @Test
    void autoBrokerAndServerNameFlags() throws ParseException {
        AppConfig config = AppSetup.fromCommandLine(parse(
                "--auto-broker", "--server-name", "Keller", "--embedded-broker-ws-port", "9083"));
        assertTrue(config.autoBroker());
        assertEquals("Keller", config.serverName());
        assertEquals(9083, config.embeddedBrokerWsPort());
    }

    @Test
    void modeParseAcceptsAllValues() {
        assertEquals(AppMode.SERVER, AppMode.parse("Server"));
        assertEquals(AppMode.CLIENT, AppMode.parse("client"));
        assertEquals(AppMode.COMBINED, AppMode.parse("COMBINED"));
        assertTrue(AppMode.SERVER.actsAsServer());
        assertFalse(AppMode.SERVER.actsAsClient());
        assertTrue(AppMode.COMBINED.actsAsServer());
        assertTrue(AppMode.COMBINED.actsAsClient());
        assertFalse(AppMode.CLIENT.actsAsServer());
    }
}
