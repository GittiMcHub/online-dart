package it.tobaben.dart.application;

import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.x01.X01Game;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {

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

        EngineUpdate lastUpdate() {
            return updates.get(updates.size() - 1);
        }
    }

    RecordingPorts ports = new RecordingPorts();
    RegisteredPlayer player1 = new RegisteredPlayer(0, new Player("Player1"), 1);
    RegisteredPlayer player2 = new RegisteredPlayer(1, new Player("Player2"), 2);

    private GameEngine startedEngine(int startScore) {
        GameEngine engine = new GameEngine(
                new X01Game(startScore, false, false), List.of(player1, player2),
                1, 1, 10, HouseRules.allOn(), ports, ports);
        engine.start();
        return engine;
    }

    @Test
    void startShouldPublishRunningStateAndSpielstart() {
        GameEngine engine = startedEngine(301);
        assertEquals(List.of(Sound.SPIELSTART), ports.sounds);
        assertEquals(1, ports.updates.size());
        EngineUpdate update = ports.lastUpdate();
        assertEquals(EngineState.RUNNING, update.state());
        assertEquals(player1, update.currentPlayer());
        assertEquals(3, update.freieWuerfe());
        assertEquals(1, update.gameId());
        assertEquals(10, update.penaltyCostCents());
        assertEquals(EngineState.RUNNING, engine.getState());
    }

    @Test
    void inputsFromWrongDartboardAreIgnoredDuringTurn() {
        GameEngine engine = startedEngine(301);
        int updatesBefore = ports.updates.size();
        engine.handle(DartboardInput.ofThrow(2, Segment.TRIPLE_20)); // player 2's board
        engine.handle(DartboardInput.next(2));
        assertEquals(updatesBefore, ports.updates.size());
        assertEquals(301, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));
        assertEquals(EngineState.RUNNING, engine.getState());
    }

    @Test
    void threeDartsShouldEndTurnIntoWaiting() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        assertEquals(2, ports.lastUpdate().freieWuerfe());
        assertEquals(20, ports.lastUpdate().lastThrowValue());
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));

        assertEquals(EngineState.WAITING, engine.getState());
        EngineUpdate update = ports.lastUpdate();
        // anthrax semantics: during WAITING the turn owner stays current with 0 free throws
        assertEquals(player1, update.currentPlayer());
        assertEquals(0, update.freieWuerfe());
        assertEquals(60, update.turnSum());
        assertEquals(241, (int) update.snapshot().scores().get(player1.player()));
    }

    @Test
    void nextDuringWaitingShouldStartNextPlayersTurn() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.next(1)); // forfeit remaining darts
        assertEquals(EngineState.WAITING, engine.getState());
        assertEquals(281, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));

        engine.handle(DartboardInput.next(2)); // NEXT may come from any board
        assertEquals(EngineState.RUNNING, engine.getState());
        EngineUpdate update = ports.lastUpdate();
        assertEquals(player2, update.currentPlayer());
        assertEquals(3, update.freieWuerfe());
        assertEquals(0, update.turnSum());
    }

    @Test
    void bounceOutMidTurnShouldVoidTurn() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        engine.handle(DartboardInput.bounceOut(1));

        assertEquals(EngineState.WAITING, engine.getState());
        assertEquals(301, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));
        assertTrue(ports.sounds.contains(Sound.RESET));
        // the thrown darts still count for the statistics
        assertEquals(1, player1.statistics().getAnzWuerfeSpiel());
        assertEquals(60, player1.statistics().getMaxPunkteProSpielzug());
    }

    @Test
    void bounceOutDuringWaitingShouldUndoTheTurn() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        assertEquals(241, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));

        engine.handle(DartboardInput.bounceOut(2)); // reported from any board
        assertEquals(EngineState.WAITING, engine.getState());
        assertEquals(301, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));

        engine.handle(DartboardInput.next(1));
        assertEquals(player2, ports.lastUpdate().currentPlayer());
    }

    @Test
    void throwsDuringWaitingAreVoid() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.next(1)); // empty turn -> WAITING
        int updatesBefore = ports.updates.size();
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        assertEquals(updatesBefore, ports.updates.size());
        assertEquals(301, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));
    }

    @Test
    void schnapszahlAtTurnEndShouldCostPenalty() {
        GameEngine engine = startedEngine(50);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_9));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_8));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_11)); // 50-28 = 22
        assertEquals(1, player1.statistics().getAnzStrafpunkte());
        assertTrue(ports.sounds.contains(Sound.STRAFE));
    }

    @Test
    void schnapszahlNotPaidTwiceWhenScoreUnchanged() {
        GameEngine engine = startedEngine(50);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_9));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_8));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_11)); // 22 -> penalty
        engine.handle(DartboardInput.next(1));   // start player 2's turn
        engine.handle(DartboardInput.next(2));   // player 2 forfeits his turn
        engine.handle(DartboardInput.next(2));   // start player 1's turn
        // player 1 again: bust -> score stays 22, no second Schnapszahl penalty
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        assertEquals(EngineState.WAITING, engine.getState());
        assertEquals(22, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));
        assertEquals(1, player1.statistics().getAnzStrafpunkte());
    }

    @Test
    void wallHitShouldCostPenaltyAndConsumeDart() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.WALL_HIT));
        assertTrue(ports.sounds.contains(Sound.STRAFE));
        assertEquals(1, player1.statistics().getAnzWandTreffer());
        assertEquals(1, player1.statistics().getAnzStrafpunkte());
        assertEquals(0, ports.lastUpdate().lastThrowValue());
        assertEquals(2, ports.lastUpdate().freieWuerfe());
        assertEquals(301, (int) ports.lastUpdate().snapshot().scores().get(player1.player()));
    }

    @Test
    void missShouldPlayResetAndCount() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.BOARD_HIT));
        assertTrue(ports.sounds.contains(Sound.RESET));
        assertEquals(1, player1.statistics().getAnzRandTreffer());
        assertEquals(0, player1.statistics().getAnzStrafpunkte());
    }

    @Test
    void bustShouldPlayUeberworfenAndCount() {
        GameEngine engine = startedEngine(50);
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20)); // bust
        assertTrue(ports.sounds.contains(Sound.UEBERWORFEN));
        assertEquals(1, player1.statistics().getAnzUeberworfen());
        assertEquals(EngineState.WAITING, engine.getState());
    }

    @Test
    void segmentSoundsAreMapped() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.DOUBLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.BULLS_EYE));
        engine.handle(DartboardInput.next(2));
        engine.handle(DartboardInput.ofThrow(2, Segment.SINGLE_20));
        assertEquals(List.of(Sound.SPIELSTART, Sound.TRIPLE, Sound.DOUBLE, Sound.BULLSEYE,
                Sound.TREFFER, Sound.TREFFER), ports.sounds);
    }

    @Test
    void maxPointsTurnShouldPlayMaxpointsSound() {
        GameEngine engine = startedEngine(301);
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.TRIPLE_20)); // 180
        assertTrue(ports.sounds.contains(Sound.MAXPOINTS));
    }

    @Test
    void winShouldFinishGameWithPlacementPenalties() {
        GameEngine engine = startedEngine(40);
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_20)); // 0 -> win

        assertTrue(ports.sounds.contains(Sound.WINNER));
        assertEquals(EngineState.FINISHED, engine.getState());
        assertEquals(List.of(player1, player2), engine.getRanking());
        // placement penalties: place 1 -> 1 point, place 2 -> 2 points
        assertEquals(1, player1.statistics().getAnzStrafpunkte());
        assertEquals(2, player2.statistics().getAnzStrafpunkte());
        assertEquals(2, player1.statistics().getAnzMinWuerfeBisSpielende());
        assertEquals(40, player1.statistics().getHighestFinish());
        assertFalse(engine.isComplete());

        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_5)); // void
        assertFalse(engine.isComplete());
        engine.handle(DartboardInput.next(2)); // final NEXT from any board
        assertTrue(engine.isComplete());
    }

    @Test
    void houseRulesCanBeDisabled() {
        GameEngine engine = new GameEngine(
                new X01Game(50, false, false), List.of(player1, player2),
                1, 1, 10, HouseRules.allOff(), ports, ports);
        engine.start();
        engine.handle(DartboardInput.ofThrow(1, Segment.WALL_HIT));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_9));
        engine.handle(DartboardInput.ofThrow(1, Segment.SINGLE_19)); // 50-28 = 22 Schnapszahl
        assertEquals(EngineState.WAITING, engine.getState());
        assertEquals(0, player1.statistics().getAnzStrafpunkte());
        assertEquals(1, player1.statistics().getAnzWandTreffer()); // counted, not fined
    }
}
