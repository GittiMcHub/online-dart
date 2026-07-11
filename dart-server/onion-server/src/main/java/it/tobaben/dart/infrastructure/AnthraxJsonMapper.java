package it.tobaben.dart.infrastructure;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.tobaben.dart.application.EngineState;
import it.tobaben.dart.application.EngineUpdate;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.game.x01.X01Game;

import java.util.Map;

/**
 * Renders an EngineUpdate into the anthrax status/gameUpdate JSON
 * (dart-server/schema.json): German field names, players as Spieler objects
 * with embedded statistik, gameState as string. For non-X01 modes the fields
 * "gameMode" and "modeData" are appended (schema extension, see TODO Phase 5);
 * "punktestand" then carries the mode's main score (Cricket: points).
 */
public final class AnthraxJsonMapper {

    private static final Gson GSON = new Gson();

    private AnthraxJsonMapper() {
    }

    public static String toJson(EngineUpdate update) {
        JsonObject root = new JsonObject();

        JsonArray platzierung = new JsonArray();
        for (RegisteredPlayer registered : update.ranking()) {
            platzierung.add(spieler(registered, update));
        }
        root.add("spielerPlatzierung", platzierung);

        JsonArray reihenfolge = new JsonArray();
        for (RegisteredPlayer registered : update.playersInOrder()) {
            reihenfolge.add(spieler(registered, update));
        }
        root.add("spielerReihenfolge", reihenfolge);

        root.add("currentPlayer", spieler(update.currentPlayer(), update));
        root.addProperty("kostenStrafpunkte", update.penaltyCostCents());
        root.addProperty("anzahlSpiele", update.totalGames());
        root.addProperty("spielId", update.gameId());
        root.addProperty("punkteSpielzug", update.turnSum());
        root.addProperty("letzterWurf", update.lastThrowValue());
        root.addProperty("gameState", update.state().name());

        if (!X01Game.GAME_MODE.equals(update.snapshot().gameMode())) {
            root.addProperty("gameMode", update.snapshot().gameMode());
            JsonObject modeData = new JsonObject();
            for (Map.Entry<String, String> entry : update.snapshot().modeData().entrySet()) {
                modeData.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("modeData", modeData);
        }
        return GSON.toJson(root);
    }

    private static JsonObject spieler(RegisteredPlayer registered, EngineUpdate update) {
        JsonObject spieler = new JsonObject();
        spieler.addProperty("id", registered.id());
        spieler.addProperty("dartboardId", registered.dartboardId());
        spieler.addProperty("name", registered.getName());
        Integer score = update.snapshot().scores().get(registered.player());
        spieler.addProperty("punktestand", score == null ? 0 : score);
        // anthrax semantics: only the player at the board has free throws,
        // outside RUNNING nobody has
        boolean atTheBoard = registered.equals(update.currentPlayer())
                && update.state() == EngineState.RUNNING;
        spieler.addProperty("freieWuerfe", atTheBoard ? update.freieWuerfe() : 0);
        spieler.add("statistik", GSON.toJsonTree(registered.statistics()));
        return spieler;
    }
}
