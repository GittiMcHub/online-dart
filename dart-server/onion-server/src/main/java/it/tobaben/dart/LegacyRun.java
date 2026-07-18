package it.tobaben.dart;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.application.TournamentEngine;
import it.tobaben.dart.infrastructure.EmbeddedBroker;
import it.tobaben.dart.infrastructure.MqttAdapter;
import it.tobaben.dart.infrastructure.ServerConfig;
import it.tobaben.dart.infrastructure.ServerSetup;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The original one-shot server run (setup -> MQTT -> one tournament -> exit),
 * used whenever --player is given so existing invocations keep their exact
 * behavior. The long-lived web mode lives in Main/AppContext.
 */
public final class LegacyRun {

    private LegacyRun() {
    }

    public static void run(String[] args) throws Exception {
        Optional<ServerConfig> maybeConfig = ServerSetup.fromArgs(args, System.in, System.out);
        if (maybeConfig.isEmpty()) {
            return; // --help
        }
        ServerConfig config = maybeConfig.get();

        EmbeddedBroker broker = null;
        if (config.embeddedBroker()) {
            broker = new EmbeddedBroker(config.mqtt(), config.embeddedBrokerWsPort());
            broker.start();
            System.out.println("[MAIN] Eingebetteter MQTT-Broker gestartet: TCP-Port "
                    + config.mqtt().port() + ", WebSocket-Port " + config.embeddedBrokerWsPort()
                    + ", Benutzer " + config.mqtt().username());
        }

        BlockingQueue<DartboardInput> inputQueue = new LinkedBlockingQueue<>();
        MqttAdapter mqtt = new MqttAdapter(config.mqtt(), inputQueue);
        mqtt.connect();

        TournamentEngine tournament = new TournamentEngine(
                inputQueue, config.players(), config.gameFactory(),
                config.games(), config.penaltyCostCents(), config.houseRules(),
                mqtt, mqtt);

        System.out.println("[MAIN] Turnier startet: " + config.games() + " Spiel(e), Modus "
                + config.gameMode() + ", " + config.players().size() + " Spieler");
        tournament.runTournament();

        System.out.println("[MAIN] Turnier beendet! Ergebnisse:");
        List<List<RegisteredPlayer>> results = tournament.getGameResults();
        for (int i = 0; i < results.size(); i++) {
            System.out.println("[MAIN] Spiel " + (i + 1) + ":");
            List<RegisteredPlayer> ranking = results.get(i);
            for (int place = 0; place < ranking.size(); place++) {
                System.out.println("[MAIN]   Platz " + (place + 1) + ": " + ranking.get(place).getName());
            }
        }
        for (RegisteredPlayer registered : config.players()) {
            System.out.println("[MAIN] Statistik " + registered.getName()
                    + ": Strafpunkte=" + registered.statistics().getAnzStrafpunkte()
                    + " (" + registered.statistics().getAnzStrafpunkte() * config.penaltyCostCents() + " Cent)"
                    + ", Avg Turnier=" + registered.statistics().getAvgTurnier()
                    + ", Würfe Turnier=" + registered.statistics().getAnzWuerfeTurnier());
        }
        mqtt.disconnect();
        if (broker != null) {
            broker.stop();
        }
    }
}
