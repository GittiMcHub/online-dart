package it.tobaben.dart.game.x01;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.DartLikeGame;
import it.tobaben.dart.game.GameEvent;
import it.tobaben.dart.game.GameSnapshot;
import it.tobaben.dart.game.board.DartSet;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.BustException;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import it.tobaben.dart.game.exceptions.PlayerFinishedException;

import java.util.*;

/**
 * Generic X01 countdown game (301, 501, or any free start score), optionally with
 * double-in (score only counts after the first double was hit) and double-out
 * (the finishing throw must be a double; a remaining score of 1 is a bust).
 */
public class X01Game implements DartLikeGame {

    public static final String GAME_MODE = "X01";

    private final int THROW_LIMIT_PER_ROUND = 3;
    private final int MAX_POINTS_PER_ROUND = 180;

    private final int startScore;
    private final boolean doubleIn;
    private final boolean doubleOut;

    private boolean started;
    private boolean over;
    private final List<Player> players;
    private final Set<Player> openedPlayers;
    private Player currentPlayer;
    private DartSet currentDartSet;
    private CountingScoreboard scoreboard;
    private Segment lastThrow;
    private int turnStartScore;
    private TurnRecord lastTurn;

    private record TurnRecord(Player player, int scoreBefore) {
    }

    private final List<DartSet> dartSetHistory;
    private final List<GameEvent> lastTurnEvents;

    public X01Game(int startScore, boolean doubleIn, boolean doubleOut) {
        if (startScore <= 0) {
            throw new IllegalArgumentException("startScore must be > 0");
        }
        this.startScore = startScore;
        this.doubleIn = doubleIn;
        this.doubleOut = doubleOut;
        this.dartSetHistory = new ArrayList<>();
        this.lastTurnEvents = new ArrayList<>();
        this.players = new ArrayList<>();
        this.openedPlayers = new HashSet<>();
        this.started = false;
        this.over = false;
    }

    public Map<Player, Integer> getScoreboard(){
        if(this.scoreboard == null){
            return Map.of();
        }
        return this.scoreboard.getScores();
    }

    public List<DartSet> getDartSetHistory(){
        return List.copyOf(this.dartSetHistory);
    }

    @Override
    public void addPlayer(Player player) {
        if(this.started){
            return;
        }
        this.players.add(player);
        if(this.currentPlayer == null){
            this.currentPlayer = player;
        }
    }

    @Override
    public void removePlayer(Player player) {
        if(this.started){
            return;
        }
        this.players.remove(player);
        if(this.players.isEmpty()){
            this.currentPlayer = null;
        }
    }

    @Override
    public void playTurn(Segment segment) throws InvalidStateException, GameOverException {
        requirePlayable();
        this.lastTurnEvents.clear();
        ensureStarted();
        if(this.currentDartSet == null){
            this.currentDartSet = new DartSet(this.currentPlayer, THROW_LIMIT_PER_ROUND);
            this.scoreboard.startCount(this.currentPlayer);
            this.turnStartScore = this.scoreboard.getScore(this.currentPlayer);
        }
        currentDartSet.addThrow(segment);
        this.lastThrow = segment;
        this.lastTurnEvents.add(GameEvent.THROW);

        int effectiveScore = segment.getScore();
        if(this.doubleIn && !this.openedPlayers.contains(this.currentPlayer)){
            if(segment.isDouble()){
                this.openedPlayers.add(this.currentPlayer);
            } else {
                // not opened yet: the dart is thrown but does not score
                effectiveScore = 0;
            }
        }

        try {
            if(this.doubleOut && this.scoreboard.getCurrentScore() - effectiveScore == 1){
                // a remaining score of 1 cannot be finished with a double
                throw new BustException("Remaining score of 1 cannot be finished with double-out");
            }
            boolean finishingThrow = !this.doubleOut || segment.isDouble();
            scoreboard.changeScore(effectiveScore, finishingThrow);
        } catch (BustException e) {
            currentDartSet.busted();
            this.scoreboard.clearCommit();
            this.lastTurnEvents.add(GameEvent.BUST);
        } catch (PlayerFinishedException e) {
            this.currentDartSet.finish();
            this.lastTurnEvents.add(GameEvent.PLAYER_FINISHED);
        }

        if(currentDartSet.isDone()){
            if(currentDartSet.getScore() == MAX_POINTS_PER_ROUND){
                this.lastTurnEvents.add(GameEvent.MAX_POINTS);
            }
            this.dartSetHistory.add(currentDartSet);
            this.currentDartSet = null;
            this.lastTurn = new TurnRecord(this.currentPlayer, this.turnStartScore);
            this.lastTurnEvents.add(GameEvent.TURN_ENDED);
            try {
                this.scoreboard.commit();
            } catch (GameOverException e) {
                this.over = true;
                this.lastTurnEvents.add(GameEvent.GAME_OVER);
                throw e;
            }
            this.currentPlayer = getNextPlayer();
        }
    }

    private void ensureStarted(){
        if(!this.started){
            this.scoreboard = new CountingScoreboard(Set.copyOf(this.players), this.startScore, 0);
            this.started = true;
            this.lastTurnEvents.add(GameEvent.GAME_STARTED);
        }
    }

    private void requirePlayable() throws InvalidStateException {
        if(this.players.isEmpty() || currentPlayer == null){
            throw new InvalidStateException("Game must be played by at least 1 Player OR currentPlayer is Null");
        }
        if(this.over){
            throw new InvalidStateException("Game is already over");
        }
    }

    @Override
    public void endTurn() throws InvalidStateException, GameOverException {
        requirePlayable();
        this.lastTurnEvents.clear();
        ensureStarted();
        if(this.currentDartSet != null){
            if(this.currentDartSet.getScore() == MAX_POINTS_PER_ROUND){
                this.lastTurnEvents.add(GameEvent.MAX_POINTS);
            }
            this.dartSetHistory.add(this.currentDartSet);
            this.currentDartSet = null;
            this.lastTurn = new TurnRecord(this.currentPlayer, this.turnStartScore);
            this.lastTurnEvents.add(GameEvent.TURN_ENDED);
            try {
                this.scoreboard.commit();
            } catch (GameOverException e) {
                this.over = true;
                this.lastTurnEvents.add(GameEvent.GAME_OVER);
                throw e;
            }
        } else {
            // no dart thrown, the turn is forfeited unchanged
            this.scoreboard.clearCommit();
            this.lastTurn = new TurnRecord(this.currentPlayer, this.scoreboard.getScore(this.currentPlayer));
            this.lastTurnEvents.add(GameEvent.TURN_ENDED);
        }
        this.currentPlayer = getNextPlayer();
    }

    @Override
    public void abortTurn() throws InvalidStateException {
        requirePlayable();
        this.lastTurnEvents.clear();
        ensureStarted();
        this.currentDartSet = null;
        this.scoreboard.clearCommit();
        this.lastTurn = new TurnRecord(this.currentPlayer, this.scoreboard.getScore(this.currentPlayer));
        this.lastTurnEvents.add(GameEvent.TURN_ENDED);
        this.currentPlayer = getNextPlayer();
    }

    @Override
    public void undoLastTurn() {
        this.lastTurnEvents.clear();
        if(this.lastTurn == null || this.scoreboard == null){
            return;
        }
        this.scoreboard.overrideScore(this.lastTurn.player(), this.lastTurn.scoreBefore());
        if(this.scoreboard.getRanking().contains(this.lastTurn.player())){
            this.scoreboard.removeFromRanking(this.lastTurn.player());
            this.over = false;
        }
    }

    private Player getNextPlayer() throws InvalidStateException{
        int currentPlayerIndex = this.players.indexOf(currentPlayer);
        int nextPlayerIndex = (currentPlayerIndex + 1) % players.size();
        for(int i = 0; i < this.players.size(); i++){
            if(! this.scoreboard.getRanking().contains(this.players.get(nextPlayerIndex))){
                return this.players.get(nextPlayerIndex);
            } else {
                nextPlayerIndex = (nextPlayerIndex + 1) % players.size();
            }
        }
        throw new InvalidStateException("There is no next player!");
    }

    @Override
    public List<Player> getRanking() {
        if(this.scoreboard == null){
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
        Map<Player, Integer> scores;
        if (this.scoreboard == null) {
            scores = new HashMap<>();
            for (Player player : this.players) {
                scores.put(player, this.startScore);
            }
        } else {
            scores = this.scoreboard.getScores();
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
                Map.of(
                        "startScore", String.valueOf(this.startScore),
                        "doubleIn", String.valueOf(this.doubleIn),
                        "doubleOut", String.valueOf(this.doubleOut)
                )
        );
    }

}
