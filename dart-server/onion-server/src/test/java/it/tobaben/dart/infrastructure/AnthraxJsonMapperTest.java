package it.tobaben.dart.infrastructure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.tobaben.dart.application.*;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.cricket.CricketGame;
import it.tobaben.dart.game.x01.X01Game;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the JSON against dart-server/schema.json: required fields, German
 * names, statistik sub-object, gameState string values.
 */
class AnthraxJsonMapperTest {

    static class CapturingPorts implements GameUpdatePublisherPort, SoundPublisherPort {
        final List<EngineUpdate> updates = new ArrayList<>();

        @Override
        public void publish(EngineUpdate update) {
            updates.add(update);
        }

        @Override
        public void play(Sound sound) {
        }

        EngineUpdate lastUpdate() {
            return updates.get(updates.size() - 1);
        }
    }

    private static final String[] SCHEMA_ROOT_FIELDS = {
            "spielerPlatzierung", "spielerReihenfolge", "currentPlayer",
            "kostenStrafpunkte", "anzahlSpiele", "spielId", "punkteSpielzug",
            "letzterWurf", "gameState"};

    private static final String[] SCHEMA_SPIELER_FIELDS = {
            "id", "dartboardId", "name", "punktestand", "freieWuerfe", "statistik"};

    private static final String[] SCHEMA_STATISTIK_FIELDS = {
            "anzWuerfeSpiel", "anzWuerfeTurnier", "summeSpiel", "summeTurnier",
            "anzMinWuerfeBisSpielende", "maxPunkteProSpielzug", "avgSpiel", "avgTurnier",
            "anzStrafpunkte", "anzRandTreffer", "anzWandTreffer", "anzBull", "anzBullseye",
            "anzEinerFeld", "anzDoubleFeld", "anzTripleFeld", "anzUeberworfen"};

    private CapturingPorts playX01Update() {
        CapturingPorts ports = new CapturingPorts();
        RegisteredPlayer player1 = new RegisteredPlayer(0, new Player("Alice"), 1);
        RegisteredPlayer player2 = new RegisteredPlayer(1, new Player("Bob"), 2);
        GameEngine engine = new GameEngine(new X01Game(301, false, false),
                List.of(player1, player2), 2, 3, 10, HouseRules.allOn(), ports, ports);
        engine.start();
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        return ports;
    }

    @Test
    void x01UpdateContainsAllSchemaFields() {
        JsonObject root = JsonParser.parseString(AnthraxJsonMapper.toJson(playX01Update().lastUpdate())).getAsJsonObject();

        for (String field : SCHEMA_ROOT_FIELDS) {
            assertTrue(root.has(field), "missing root field: " + field);
        }
        // X01 stays byte-compatible: no extension fields
        assertFalse(root.has("gameMode"));
        assertFalse(root.has("modeData"));

        JsonObject currentPlayer = root.getAsJsonObject("currentPlayer");
        for (String field : SCHEMA_SPIELER_FIELDS) {
            assertTrue(currentPlayer.has(field), "missing spieler field: " + field);
        }
        JsonObject statistik = currentPlayer.getAsJsonObject("statistik");
        for (String field : SCHEMA_STATISTIK_FIELDS) {
            assertTrue(statistik.has(field), "missing statistik field: " + field);
        }
    }

    @Test
    void x01UpdateCarriesGameValues() {
        JsonObject root = JsonParser.parseString(AnthraxJsonMapper.toJson(playX01Update().lastUpdate())).getAsJsonObject();

        assertEquals("RUNNING", root.get("gameState").getAsString());
        assertEquals(2, root.get("spielId").getAsInt());
        assertEquals(3, root.get("anzahlSpiele").getAsInt());
        assertEquals(10, root.get("kostenStrafpunkte").getAsInt());
        assertEquals(60, root.get("letzterWurf").getAsInt());
        assertEquals(60, root.get("punkteSpielzug").getAsInt());
        assertEquals(2, root.getAsJsonArray("spielerReihenfolge").size());
        assertEquals(0, root.getAsJsonArray("spielerPlatzierung").size());

        JsonObject currentPlayer = root.getAsJsonObject("currentPlayer");
        assertEquals("Alice", currentPlayer.get("name").getAsString());
        assertEquals(1, currentPlayer.get("dartboardId").getAsInt());
        // staged turn: committed score still 301, two darts left
        assertEquals(301, currentPlayer.get("punktestand").getAsInt());
        assertEquals(2, currentPlayer.get("freieWuerfe").getAsInt());
        assertEquals(1, currentPlayer.getAsJsonObject("statistik").get("anzWuerfeSpiel").getAsInt());
        assertEquals(1, currentPlayer.getAsJsonObject("statistik").get("anzTripleFeld").getAsInt());
    }

    @Test
    void waitingStateShowsTurnOwnerWithZeroThrows() {
        CapturingPorts ports2 = new CapturingPorts();
        RegisteredPlayer alice = new RegisteredPlayer(0, new Player("Alice"), 1);
        RegisteredPlayer bob = new RegisteredPlayer(1, new Player("Bob"), 2);
        GameEngine engine = new GameEngine(new X01Game(301, false, false),
                List.of(alice, bob), 1, 1, 0, HouseRules.allOn(), ports2, ports2);
        engine.start();
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));

        JsonObject root = JsonParser.parseString(AnthraxJsonMapper.toJson(ports2.lastUpdate())).getAsJsonObject();
        assertEquals("WAITING", root.get("gameState").getAsString());
        JsonObject currentPlayer = root.getAsJsonObject("currentPlayer");
        assertEquals("Alice", currentPlayer.get("name").getAsString());
        assertEquals(0, currentPlayer.get("freieWuerfe").getAsInt());
        assertEquals(241, currentPlayer.get("punktestand").getAsInt());
    }

    @Test
    void cricketUpdateAddsModeDataExtension() {
        CapturingPorts ports = new CapturingPorts();
        RegisteredPlayer alice = new RegisteredPlayer(0, new Player("Alice"), 1);
        RegisteredPlayer bob = new RegisteredPlayer(1, new Player("Bob"), 2);
        GameEngine engine = new GameEngine(new CricketGame(),
                List.of(alice, bob), 1, 1, 0, HouseRules.allOn(), ports, ports);
        engine.start();
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20)); // closed + 20 points

        JsonObject root = JsonParser.parseString(AnthraxJsonMapper.toJson(ports.lastUpdate())).getAsJsonObject();
        assertEquals("CRICKET", root.get("gameMode").getAsString());
        assertEquals("3", root.getAsJsonObject("modeData").get("marks:Alice:20").getAsString());
        // punktestand carries the cricket points
        assertEquals(20, root.getAsJsonObject("currentPlayer").get("punktestand").getAsInt());
    }

    @Test
    void soundJsonMatchesReadmeFormat() {
        assertEquals("{\"sound\":\"BULLSEYE\"}", MqttAdapter.soundJson(Sound.BULLSEYE));
        assertEquals("{\"sound\":\"MAXPOINTS\"}", MqttAdapter.soundJson(Sound.MAXPOINTS));
    }

    @Test
    void dartboardTopicIdExtraction() {
        assertEquals(4, MqttAdapter.extractDartboardId("dartboard/4"));
        assertEquals(12, MqttAdapter.extractDartboardId("dartboard/12"));
        assertEquals(-1, MqttAdapter.extractDartboardId("dartboard/abc"));
        assertEquals(-1, MqttAdapter.extractDartboardId("status/gameUpdate"));
        assertEquals(-1, MqttAdapter.extractDartboardId("dartboard/"));
    }
}
