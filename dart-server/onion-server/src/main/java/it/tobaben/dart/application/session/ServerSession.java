package it.tobaben.dart.application.session;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.application.TournamentEngine;
import it.tobaben.dart.application.lobby.LobbyPlayer;
import it.tobaben.dart.application.lobby.LobbyResult;
import it.tobaben.dart.application.lobby.LobbyService;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.common.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Long-lived lifecycle around the (unchanged, per-tournament) TournamentEngine:
 *
 * <pre>IDLE → openLobby() → LOBBY → startTournament(cfg) → RUNNING → (finished/abort) → IDLE</pre>
 *
 * Any number of tournaments can be played without restarting the process. Each
 * start builds a fresh RegisteredPlayer list from the lobby (Player equality is
 * identity-based, so player objects must live exactly one tournament). The
 * lobby keeps its players across tournaments — reopening the lobby shows the
 * same lineup for the next round.
 *
 * Threading: all mutators are synchronized and callable from web/MQTT threads;
 * throw processing stays alone on the "tournament" thread via the input queue.
 */
public class ServerSession {

    private final BlockingQueue<DartboardInput> inputQueue;
    private final GameUpdatePublisherPort updatePublisher;
    private final SoundPublisherPort soundPublisher;
    private final LobbyService lobby;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private SessionPhase phase = SessionPhase.IDLE;
    private TournamentConfig gameConfig;
    private Thread tournamentThread;
    private List<RegisteredPlayer> tournamentPlayers = List.of();
    private List<List<RegisteredPlayer>> lastResults = List.of();

    public ServerSession(BlockingQueue<DartboardInput> inputQueue,
                         LobbyService lobby,
                         GameUpdatePublisherPort updatePublisher,
                         SoundPublisherPort soundPublisher) {
        this.inputQueue = inputQueue;
        this.lobby = lobby;
        this.updatePublisher = updatePublisher;
        this.soundPublisher = soundPublisher;
    }

    /** Fires after every state change (web layer pushes SSE, lobby adapter republishes). */
    public void addChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    public synchronized SessionPhase getPhase() {
        return this.phase;
    }

    public LobbyService getLobby() {
        return this.lobby;
    }

    public synchronized TournamentConfig getGameConfig() {
        return this.gameConfig;
    }

    /** @return the players of the running (or last finished) tournament */
    public synchronized List<RegisteredPlayer> getTournamentPlayers() {
        return this.tournamentPlayers;
    }

    /** @return final rankings of the last tournament's games, in played order */
    public synchronized List<List<RegisteredPlayer>> getLastResults() {
        return this.lastResults;
    }

    public synchronized LobbyResult openLobby() {
        if (this.phase == SessionPhase.RUNNING) {
            return LobbyResult.failure("Turnier läuft bereits");
        }
        this.phase = SessionPhase.LOBBY;
        fireChange();
        return LobbyResult.success();
    }

    public synchronized LobbyResult closeLobby() {
        if (this.phase != SessionPhase.LOBBY) {
            return LobbyResult.failure("Lobby ist nicht geöffnet");
        }
        this.phase = SessionPhase.IDLE;
        fireChange();
        return LobbyResult.success();
    }

    public synchronized LobbyResult joinLobby(String name, int dartboardId, String clientId) {
        if (this.phase == SessionPhase.RUNNING) {
            return LobbyResult.failure("Turnier läuft bereits");
        }
        if (this.phase != SessionPhase.LOBBY) {
            return LobbyResult.failure("Lobby ist nicht geöffnet");
        }
        LobbyResult result = this.lobby.join(name, dartboardId, clientId);
        if (result.ok()) {
            fireChange();
        }
        return result;
    }

    public synchronized LobbyResult leaveLobby(String name, String clientId) {
        LobbyResult result = LobbyPlayer.LOCAL_CLIENT_ID.equals(clientId)
                ? (this.lobby.remove(name)
                        ? LobbyResult.success()
                        : LobbyResult.failure("Spieler '" + name + "' ist nicht in der Lobby"))
                : this.lobby.leave(name, clientId);
        if (result.ok()) {
            fireChange();
        }
        return result;
    }

    /** Host-side reordering of the lobby lineup (web UI). */
    public synchronized LobbyResult movePlayer(String name, int offset) {
        if (this.phase != SessionPhase.LOBBY) {
            return LobbyResult.failure("Lobby ist nicht geöffnet");
        }
        LobbyResult result = this.lobby.move(name, offset);
        if (result.ok()) {
            fireChange();
        }
        return result;
    }

    /** Removes every player of a disconnected client (MQTT Last-Will). */
    public synchronized void clientDisconnected(String clientId) {
        if (this.phase == SessionPhase.LOBBY && this.lobby.leaveAll(clientId)) {
            fireChange();
        }
    }

    public synchronized LobbyResult startTournament(TournamentConfig config) {
        if (this.phase == SessionPhase.RUNNING) {
            return LobbyResult.failure("Turnier läuft bereits");
        }
        if (this.phase != SessionPhase.LOBBY) {
            return LobbyResult.failure("Lobby ist nicht geöffnet");
        }
        List<LobbyPlayer> lineup = this.lobby.getPlayers();
        if (lineup.isEmpty()) {
            return LobbyResult.failure("Mindestens ein Spieler wird benötigt");
        }

        List<RegisteredPlayer> players = new ArrayList<>();
        for (LobbyPlayer waiting : lineup) {
            players.add(new RegisteredPlayer(players.size(), new Player(waiting.name()), waiting.dartboardId()));
        }
        this.inputQueue.clear(); // stray throws from before the start must not count
        TournamentEngine engine = new TournamentEngine(
                this.inputQueue, players, config.gameFactory(),
                config.games(), config.penaltyCostCents(), config.houseRules(),
                this.updatePublisher, this.soundPublisher);

        this.gameConfig = config;
        this.tournamentPlayers = List.copyOf(players);
        this.phase = SessionPhase.RUNNING;
        this.tournamentThread = new Thread(() -> runTournament(engine), "tournament");
        this.tournamentThread.start();
        fireChange();
        return LobbyResult.success();
    }

    public synchronized LobbyResult abortTournament() {
        if (this.phase != SessionPhase.RUNNING) {
            return LobbyResult.failure("Es läuft kein Turnier");
        }
        this.tournamentThread.interrupt();
        return LobbyResult.success();
    }

    private void runTournament(TournamentEngine engine) {
        try {
            engine.runTournament();
        } catch (InterruptedException e) {
            System.out.println("[SESSION] Turnier abgebrochen");
        } catch (RuntimeException e) {
            System.err.println("[SESSION] Turnier mit Fehler beendet: " + e.getMessage());
        } finally {
            finishTournament(engine);
        }
    }

    private synchronized void finishTournament(TournamentEngine engine) {
        this.lastResults = engine.getGameResults();
        this.phase = SessionPhase.IDLE;
        this.tournamentThread = null;
        this.updatePublisher.publishIdle();
        fireChange();
    }

    private void fireChange() {
        for (Runnable listener : this.changeListeners) {
            listener.run();
        }
    }
}
