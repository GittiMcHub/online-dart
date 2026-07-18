package it.tobaben.dart.application.session;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.EngineUpdate;
import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.application.Sound;
import it.tobaben.dart.application.lobby.LobbyPlayer;
import it.tobaben.dart.application.lobby.LobbyService;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.game.board.Segment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServerSessionTest {

    static class RecordingPorts implements GameUpdatePublisherPort, SoundPublisherPort {
        final AtomicInteger idlePublishes = new AtomicInteger();

        @Override
        public void publish(EngineUpdate update) {
        }

        @Override
        public void publishIdle() {
            idlePublishes.incrementAndGet();
        }

        @Override
        public void play(Sound sound) {
        }
    }

    BlockingQueue<DartboardInput> queue = new LinkedBlockingQueue<>();
    RecordingPorts ports = new RecordingPorts();
    ServerSession session = new ServerSession(queue, new LobbyService(), ports, ports);

    // X01 with start score 1: one single-1 throw ends the game
    static final TournamentConfig QUICK_GAME = new TournamentConfig(
            TournamentConfig.MODE_X01, 1, false, false, 1, 0, HouseRules.allOff());

    private void awaitPhase(SessionPhase expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (session.getPhase() != expected) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timeout: Phase " + expected + " nicht erreicht (ist " + session.getPhase() + ")");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void startsInIdleWithoutLobby() {
        assertEquals(SessionPhase.IDLE, session.getPhase());
        assertFalse(session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID).ok());
    }

    @Test
    void openLobbyAllowsJoining() {
        assertTrue(session.openLobby().ok());
        assertEquals(SessionPhase.LOBBY, session.getPhase());
        assertTrue(session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID).ok());
        assertEquals(1, session.getLobby().getPlayers().size());
    }

    @Test
    void startTournamentRequiresPlayers() {
        session.openLobby();
        assertFalse(session.startTournament(QUICK_GAME).ok());
    }

    @Test
    void startTournamentRequiresLobbyPhase() {
        assertFalse(session.startTournament(QUICK_GAME).ok());
    }

    @Test
    void tournamentRunsToCompletionAndReturnsToIdle() throws InterruptedException {
        session.openLobby();
        session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID);
        assertTrue(session.startTournament(QUICK_GAME).ok());
        assertEquals(SessionPhase.RUNNING, session.getPhase());

        queue.put(DartboardInput.ofThrow(1, Segment.SINGLE_1)); // 1 -> 0: game over
        queue.put(DartboardInput.next(1)); // confirm FINISHED screen
        awaitPhase(SessionPhase.IDLE);

        assertEquals(1, session.getLastResults().size());
        assertEquals("Alice", session.getLastResults().get(0).get(0).getName());
        assertEquals(1, ports.idlePublishes.get());
        // lobby lineup survives the tournament for the next round
        assertEquals(1, session.getLobby().getPlayers().size());
    }

    @Test
    void secondTournamentWithoutRestart() throws InterruptedException {
        session.openLobby();
        session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID);
        session.startTournament(QUICK_GAME);
        queue.put(DartboardInput.ofThrow(1, Segment.SINGLE_1));
        queue.put(DartboardInput.next(1));
        awaitPhase(SessionPhase.IDLE);

        assertTrue(session.openLobby().ok());
        assertTrue(session.startTournament(QUICK_GAME).ok());
        queue.put(DartboardInput.ofThrow(1, Segment.SINGLE_1));
        queue.put(DartboardInput.next(1));
        awaitPhase(SessionPhase.IDLE);
        assertEquals(2, ports.idlePublishes.get());
    }

    @Test
    void abortInterruptsTournament() throws InterruptedException {
        session.openLobby();
        session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID);
        session.startTournament(QUICK_GAME);
        assertTrue(session.abortTournament().ok());
        awaitPhase(SessionPhase.IDLE);
        assertEquals(1, ports.idlePublishes.get());
    }

    @Test
    void joinDuringRunningIsRejected() throws InterruptedException {
        session.openLobby();
        session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID);
        session.startTournament(QUICK_GAME);
        assertFalse(session.joinLobby("Bob", 2, LobbyPlayer.LOCAL_CLIENT_ID).ok());
        session.abortTournament();
        awaitPhase(SessionPhase.IDLE);
    }

    @Test
    void freshRegisteredPlayersPerTournament() throws InterruptedException {
        session.openLobby();
        session.joinLobby("Alice", 1, LobbyPlayer.LOCAL_CLIENT_ID);
        session.startTournament(QUICK_GAME);
        List<it.tobaben.dart.application.RegisteredPlayer> first = session.getTournamentPlayers();
        queue.put(DartboardInput.ofThrow(1, Segment.SINGLE_1));
        queue.put(DartboardInput.next(1));
        awaitPhase(SessionPhase.IDLE);

        session.openLobby();
        session.startTournament(QUICK_GAME);
        assertNotSame(first.get(0), session.getTournamentPlayers().get(0));
        assertNotSame(first.get(0).player(), session.getTournamentPlayers().get(0).player());
        session.abortTournament();
        awaitPhase(SessionPhase.IDLE);
    }
}
