package it.tobaben.dart.application.session;

import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.game.DartLikeGame;
import it.tobaben.dart.game.cricket.CricketGame;
import it.tobaben.dart.game.x01.X01Game;

import java.util.function.Supplier;

/**
 * Game parameters for one tournament as configured in the lobby (the runtime
 * counterpart of the CLI flags in ServerConfig).
 */
public record TournamentConfig(
        String gameMode,
        int startScore,
        boolean doubleIn,
        boolean doubleOut,
        int games,
        int penaltyCostCents,
        HouseRules houseRules
) {

    public static final String MODE_X01 = "x01";
    public static final String MODE_CRICKET = "cricket";

    public TournamentConfig {
        if (!MODE_X01.equals(gameMode) && !MODE_CRICKET.equals(gameMode)) {
            throw new IllegalArgumentException("Unbekannter Spielmodus: " + gameMode);
        }
        if (games <= 0) {
            throw new IllegalArgumentException("Anzahl Spiele muss > 0 sein");
        }
        if (MODE_X01.equals(gameMode) && startScore <= 0) {
            throw new IllegalArgumentException("Startpunkte müssen > 0 sein");
        }
        if (penaltyCostCents < 0) {
            throw new IllegalArgumentException("Kosten pro Strafpunkt dürfen nicht negativ sein");
        }
    }

    public Supplier<DartLikeGame> gameFactory() {
        if (MODE_CRICKET.equals(this.gameMode)) {
            return CricketGame::new;
        }
        return () -> new X01Game(this.startScore, this.doubleIn, this.doubleOut);
    }
}
