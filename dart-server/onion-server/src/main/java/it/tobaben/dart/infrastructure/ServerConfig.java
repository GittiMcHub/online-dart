package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.game.DartLikeGame;
import it.tobaben.dart.game.cricket.CricketGame;
import it.tobaben.dart.game.x01.X01Game;

import java.util.List;
import java.util.function.Supplier;

/**
 * Complete resolved server configuration (MQTT + tournament + game mode + house rules).
 */
public record ServerConfig(
        MqttConfig mqtt,
        boolean embeddedBroker,
        int embeddedBrokerWsPort,
        String gameMode,
        int startScore,
        boolean doubleIn,
        boolean doubleOut,
        int games,
        int penaltyCostCents,
        List<RegisteredPlayer> players,
        HouseRules houseRules
) {

    public static final String MODE_X01 = "x01";
    public static final String MODE_CRICKET = "cricket";

    public Supplier<DartLikeGame> gameFactory() {
        if (MODE_CRICKET.equals(this.gameMode)) {
            return CricketGame::new;
        }
        return () -> new X01Game(this.startScore, this.doubleIn, this.doubleOut);
    }
}
