package it.tobaben.dart.game.cricket;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.DartLikeGame;
import it.tobaben.dart.game.GameEvent;
import it.tobaben.dart.game.GameSnapshot;
import it.tobaben.dart.game.board.DartSet;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;

import java.util.*;

/**
 * American Cricket. There is no bust; a turn always ends after three darts.
 * The game ends immediately (mid-turn possible) when the current player has
 * closed all numbers and has at least as many points as every opponent.
 */
public class CricketGame implements DartLikeGame {

    public static final String GAME_MODE = "CRICKET";

    private final int THROW_LIMIT_PER_ROUND = 3;

    private boolean started;
    private boolean over;
    private final List<Player> players;
    private Player currentPlayer;
    private DartSet currentDartSet;
    private CricketScoreboard scoreboard;
    private Segment lastThrow;
    private CricketScoreboard.PlayerState turnStartState;
    private TurnRecord lastTurn;

    private record TurnRecord(Player player, CricketScoreboard.PlayerState stateBefore) {
    }

    private final List<DartSet> dartSetHistory;
    private final List<GameEvent> lastTurnEvents;

    public CricketGame() {
        this.dartSetHistory = new ArrayList<>();
        this.lastTurnEvents = new ArrayList<>();
        this.players = new ArrayList<>();
        this.started = false;
        this.over = false;
    }

    public CricketScoreboard getScoreboard() {
        return this.scoreboard;
    }

    public List<DartSet> getDartSetHistory() {
        return List.copyOf(this.dartSetHistory);
    }

    @Override
    public void addPlayer(Player player) {
        if (this.started) {
            return;
        }
        this.players.add(player);
        if (this.currentPlayer == null) {
            this.currentPlayer = player;
        }
    }

    @Override
    public void removePlayer(Player player) {
        if (this.started) {
            return;
        }
        this.players.remove(player);
        if (this.players.isEmpty()) {
            this.currentPlayer = null;
        }
    }

    @Override
    public void playTurn(Segment segment) throws InvalidStateException, GameOverException {
        requirePlayable();
        this.lastTurnEvents.clear();
        ensureStarted();
        if (this.currentDartSet == null) {
            this.currentDartSet = new DartSet(this.currentPlayer, THROW_LIMIT_PER_ROUND);
            this.turnStartState = this.scoreboard.snapshotOf(this.currentPlayer);
        }
        currentDartSet.addThrow(segment);
        this.lastThrow = segment;
        this.lastTurnEvents.add(GameEvent.THROW);

        this.scoreboard.applyHit(this.currentPlayer, segment);

        if (this.scoreboard.isGameOver()) {
            this.currentDartSet.finish();
            this.dartSetHistory.add(this.currentDartSet);
            this.lastTurn = new TurnRecord(this.currentPlayer, this.turnStartState);
            this.currentDartSet = null;
            this.turnStartState = null;
            this.over = true;
            this.lastTurnEvents.add(GameEvent.PLAYER_FINISHED);
            this.lastTurnEvents.add(GameEvent.TURN_ENDED);
            this.lastTurnEvents.add(GameEvent.GAME_OVER);
            throw new GameOverException("Game is over: " + this.scoreboard.getRanking());
        }

        if (currentDartSet.isDone()) {
            this.dartSetHistory.add(currentDartSet);
            this.lastTurn = new TurnRecord(this.currentPlayer, this.turnStartState);
            this.currentDartSet = null;
            this.turnStartState = null;
            this.lastTurnEvents.add(GameEvent.TURN_ENDED);
            this.currentPlayer = getNextPlayer();
        }
    }

    private void ensureStarted() {
        if (!this.started) {
            this.scoreboard = new CricketScoreboard(this.players);
            this.started = true;
            this.lastTurnEvents.add(GameEvent.GAME_STARTED);
        }
    }

    private void requirePlayable() throws InvalidStateException {
        if (this.players.isEmpty() || currentPlayer == null) {
            throw new InvalidStateException("Game must be played by at least 1 Player OR currentPlayer is Null");
        }
        if (this.over) {
            throw new InvalidStateException("Game is already over");
        }
    }

    @Override
    public void endTurn() throws InvalidStateException {
        requirePlayable();
        this.lastTurnEvents.clear();
        ensureStarted();
        if (this.currentDartSet != null) {
            this.dartSetHistory.add(this.currentDartSet);
            this.lastTurn = new TurnRecord(this.currentPlayer, this.turnStartState);
        } else {
            this.lastTurn = new TurnRecord(this.currentPlayer, this.scoreboard.snapshotOf(this.currentPlayer));
        }
        this.currentDartSet = null;
        this.turnStartState = null;
        this.lastTurnEvents.add(GameEvent.TURN_ENDED);
        this.currentPlayer = getNextPlayer();
    }

    @Override
    public void abortTurn() throws InvalidStateException {
        requirePlayable();
        this.lastTurnEvents.clear();
        ensureStarted();
        if (this.turnStartState != null) {
            this.scoreboard.restore(this.currentPlayer, this.turnStartState);
        }
        this.lastTurn = new TurnRecord(this.currentPlayer, this.scoreboard.snapshotOf(this.currentPlayer));
        this.currentDartSet = null;
        this.turnStartState = null;
        this.lastTurnEvents.add(GameEvent.TURN_ENDED);
        this.currentPlayer = getNextPlayer();
    }

    @Override
    public void undoLastTurn() {
        this.lastTurnEvents.clear();
        if (this.lastTurn == null || this.scoreboard == null) {
            return;
        }
        this.scoreboard.restore(this.lastTurn.player(), this.lastTurn.stateBefore());
        this.over = this.scoreboard.isGameOver();
    }

    private Player getNextPlayer() {
        // nobody drops out mid-game in cricket, plain round robin
        int currentPlayerIndex = this.players.indexOf(currentPlayer);
        return this.players.get((currentPlayerIndex + 1) % players.size());
    }

    @Override
    public List<Player> getRanking() {
        if (this.scoreboard == null) {
            return List.of();
        }
        return this.scoreboard.getRanking();
    }

    @Override
    public Player getCurrentPlayer() {
        return this.currentPlayer;
    }

    @Override
    public boolean isOver() {
        return this.over;
    }

    @Override
    public List<GameEvent> getLastTurnEvents() {
        return List.copyOf(this.lastTurnEvents);
    }

    @Override
    public GameSnapshot getSnapshot() {
        Map<Player, Integer> scores = new HashMap<>();
        Map<String, String> modeData = new HashMap<>();
        for (Player player : this.players) {
            scores.put(player, this.scoreboard == null ? 0 : this.scoreboard.getPoints(player));
            for (Integer number : CricketScoreboard.CRICKET_NUMBERS) {
                int marks = this.scoreboard == null ? 0 : this.scoreboard.getMarks(player, number);
                modeData.put("marks:" + player.getName() + ":" + number, String.valueOf(marks));
            }
        }
        int throwsLeft = this.currentDartSet == null
                ? THROW_LIMIT_PER_ROUND
                : THROW_LIMIT_PER_ROUND - this.currentDartSet.getThrowCount();
        int turnScore = this.currentDartSet != null
                ? this.currentDartSet.getScore()
                : (this.dartSetHistory.isEmpty() ? 0 : this.dartSetHistory.get(this.dartSetHistory.size() - 1).getScore());
        return new GameSnapshot(
                GAME_MODE,
                List.copyOf(this.players),
                this.currentPlayer,
                scores,
                getRanking(),
                this.over,
                throwsLeft,
                this.lastThrow == null ? 0 : this.lastThrow.getScore(),
                turnScore,
                modeData
        );
    }

}
