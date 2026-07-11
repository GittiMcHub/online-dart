package it.tobaben.dart.application;

import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.x01.X01Game;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class TournamentEngineTest {

    static class RecordingPorts implements GameUpdatePublisherPort, SoundPublisherPort {
        final List<EngineUpdate> updates = new ArrayList<>();
        final List<Sound> sounds = new ArrayList<>();

        @Override
        public void publish(EngineUpdate update) {
            updates.add(update);
        }

        @Override
        public void play(Sound sound) {
            sounds.add(sound);
        }
    }

    @Test
    void tournamentShouldRotateStartingOrderAndAccumulateStatistics() throws InterruptedException {
        RecordingPorts ports = new RecordingPorts();
        RegisteredPlayer player1 = new RegisteredPlayer(0, new Player("Player1"), 1);
        RegisteredPlayer player2 = new RegisteredPlayer(1, new Player("Player2"), 2);
        BlockingQueue<DartboardInput> queue = new LinkedBlockingQueue<>();

        // game 1: player 1 starts and wins with two darts, final NEXT
        queue.add(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        queue.add(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        queue.add(DartboardInput.next(1));
        // game 2: reversed ranking of game 1 -> player 2 starts and wins
        queue.add(DartboardInput.ofThrow(2, Segment.SINGLE_20));
        queue.add(DartboardInput.ofThrow(2, Segment.SINGLE_20));
        queue.add(DartboardInput.next(2));

        TournamentEngine tournament = new TournamentEngine(
                queue, List.of(player1, player2),
                () -> new X01Game(40, false, false),
                2, 10, HouseRules.allOn(), ports, ports);
        tournament.runTournament();

        assertEquals(2, tournament.getGameResults().size());
        assertEquals(List.of(player1, player2), tournament.getGameResults().get(0));
        assertEquals(List.of(player2, player1), tournament.getGameResults().get(1));

        // game 2 was started by the previous last place (player 2)
        EngineUpdate firstUpdateOfGame2 = ports.updates.stream()
                .filter(update -> update.gameId() == 2)
                .findFirst().orElseThrow();
        assertEquals(player2, firstUpdateOfGame2.currentPlayer());
        assertEquals(List.of(player2, player1), firstUpdateOfGame2.playersInOrder());

        // per-game statistics were reset, tournament statistics accumulated
        assertEquals(0, player1.statistics().getAnzWuerfeSpiel()); // no dart in game 2
        assertEquals(2, player1.statistics().getAnzWuerfeTurnier());
        assertEquals(2, player2.statistics().getAnzWuerfeTurnier());
        // placement penalties: game 1 (1+2), game 2 reversed -> 3 for both
        assertEquals(3, player1.statistics().getAnzStrafpunkte());
        assertEquals(3, player2.statistics().getAnzStrafpunkte());
    }

    @Test
    void invalidConfigurationIsRejected() {
        RecordingPorts ports = new RecordingPorts();
        RegisteredPlayer player1 = new RegisteredPlayer(0, new Player("Player1"), 1);
        BlockingQueue<DartboardInput> queue = new LinkedBlockingQueue<>();

        assertThrows(IllegalArgumentException.class, () -> new TournamentEngine(
                queue, List.of(player1), () -> new X01Game(301, false, false),
                0, 10, HouseRules.allOn(), ports, ports));
        assertThrows(IllegalArgumentException.class, () -> new TournamentEngine(
                queue, List.of(), () -> new X01Game(301, false, false),
                1, 10, HouseRules.allOn(), ports, ports));
    }
}
