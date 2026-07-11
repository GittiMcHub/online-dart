package it.tobaben.dart.application;

import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.DartLikeGame;
import it.tobaben.dart.game.GameEvent;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives one game: consumes decoded dartboard inputs, applies house rules and
 * statistics, and publishes game updates and sounds. Replicates the anthrax
 * Spiel.run() flow as an event-driven state machine instead of a blocking loop:
 *
 * RUNNING  - only inputs from the current player's dartboard count. Throws go
 *            into the game; NEXT forfeits the remaining darts; bounce out (996)
 *            voids the turn. When the turn ends -> WAITING (or FINISHED).
 * WAITING  - any dartboard: NEXT starts the next turn; bounce out undoes the
 *            just-played turn (score back to turn start, idempotent).
 * FINISHED - any dartboard: NEXT completes the game (engine is done).
 */
public class GameEngine {

    private final DartLikeGame game;
    private final List<RegisteredPlayer> playersInOrder;
    private final Map<Player, RegisteredPlayer> byPlayer;
    private final HouseRules houseRules;
    private final GameUpdatePublisherPort updatePublisher;
    private final SoundPublisherPort soundPublisher;
    private final int gameId;
    private final int totalGames;
    private final int penaltyCostCents;

    private EngineState state = EngineState.UNDEFINED;
    private boolean complete = false;
    private RegisteredPlayer turnPlayer;
    private int turnStartScore;
    private int turnSum;
    private int lastThrowValue;

    public GameEngine(DartLikeGame game, List<RegisteredPlayer> playersInOrder,
                      int gameId, int totalGames, int penaltyCostCents,
                      HouseRules houseRules,
                      GameUpdatePublisherPort updatePublisher, SoundPublisherPort soundPublisher) {
        this.game = game;
        this.playersInOrder = List.copyOf(playersInOrder);
        this.byPlayer = new HashMap<>();
        for (RegisteredPlayer registered : playersInOrder) {
            this.byPlayer.put(registered.player(), registered);
        }
        this.houseRules = houseRules;
        this.updatePublisher = updatePublisher;
        this.soundPublisher = soundPublisher;
        this.gameId = gameId;
        this.totalGames = totalGames;
        this.penaltyCostCents = penaltyCostCents;
    }

    /** Registers the players in order and opens the first turn. */
    public void start() {
        for (RegisteredPlayer registered : this.playersInOrder) {
            this.game.addPlayer(registered.player());
        }
        this.soundPublisher.play(Sound.SPIELSTART);
        this.state = EngineState.RUNNING;
        beginTurn();
        publish();
    }

    public void handle(DartboardInput input) {
        switch (this.state) {
            case RUNNING -> handleRunning(input);
            case WAITING -> handleWaiting(input);
            case FINISHED -> handleFinished(input);
            case UNDEFINED -> { /* not started yet, ignore */ }
        }
    }

    private void handleRunning(DartboardInput input) {
        // during a turn only the current player's dartboard counts
        if (input.dartboardId() != this.turnPlayer.dartboardId()) {
            return;
        }
        switch (input.type()) {
            case THROW -> handleThrow(input.segment());
            case NEXT -> {
                this.soundPublisher.play(Sound.TREFFER);
                boolean gameOver = false;
                try {
                    this.game.endTurn();
                } catch (GameOverException e) {
                    gameOver = true;
                } catch (InvalidStateException e) {
                    return;
                }
                finishTurn(this.game.getLastTurnEvents(), gameOver);
                publish();
            }
            case BOUNCE_OUT -> {
                this.soundPublisher.play(Sound.RESET);
                try {
                    this.game.abortTurn();
                } catch (InvalidStateException e) {
                    return;
                }
                finishTurn(this.game.getLastTurnEvents(), false);
                publish();
            }
        }
    }

    private void handleThrow(Segment segment) {
        PlayerStatistics statistics = this.turnPlayer.statistics();
        if (segment == Segment.WALL_HIT) {
            statistics.recordWallHit();
            if (this.houseRules.wallHitPenalty()) {
                statistics.addStrafpunkte(1);
            }
            this.soundPublisher.play(Sound.STRAFE);
            this.lastThrowValue = 0;
        } else if (segment == Segment.BOARD_HIT) {
            statistics.recordMiss();
            this.soundPublisher.play(Sound.RESET);
            this.lastThrowValue = 0;
        } else {
            statistics.recordScoringThrow(segment);
            this.lastThrowValue = segment.getScore();
            this.turnSum += segment.getScore();
        }

        boolean gameOver = false;
        try {
            this.game.playTurn(segment);
        } catch (GameOverException e) {
            gameOver = true;
        } catch (InvalidStateException e) {
            return;
        }

        List<GameEvent> events = this.game.getLastTurnEvents();
        if (events.contains(GameEvent.BUST)) {
            statistics.recordBust();
            this.soundPublisher.play(Sound.UEBERWORFEN);
        } else if (events.contains(GameEvent.PLAYER_FINISHED)) {
            this.soundPublisher.play(Sound.WINNER);
            statistics.recordFinish(this.turnSum);
        } else if (segment != Segment.WALL_HIT && segment != Segment.BOARD_HIT) {
            this.soundPublisher.play(soundFor(segment));
        }

        if (events.contains(GameEvent.TURN_ENDED) || gameOver) {
            finishTurn(events, gameOver);
        }
        publish();
    }

    /** Turn-end bookkeeping shared by all turn-ending paths (anthrax "Spielzug ENDE"). */
    private void finishTurn(List<GameEvent> events, boolean gameOver) {
        PlayerStatistics statistics = this.turnPlayer.statistics();
        statistics.recordTurnSum(this.turnSum);
        if (events.contains(GameEvent.MAX_POINTS)) {
            this.soundPublisher.play(Sound.MAXPOINTS);
        }
        int scoreNow = scoreOf(this.turnPlayer);
        // a Schnapszahl already paid must not cost twice: after a bust or abort the
        // score is unchanged, so no new penalty
        if (this.houseRules.schnapszahlPenalty()
                && scoreNow != this.turnStartScore
                && HouseRules.isSchnapszahl(scoreNow)) {
            this.soundPublisher.play(Sound.STRAFE);
            statistics.addStrafpunkte(1);
        }
        if (gameOver) {
            if (this.houseRules.placementPenalty()) {
                List<RegisteredPlayer> ranking = getRanking();
                for (int place = 0; place < ranking.size(); place++) {
                    ranking.get(place).statistics().addStrafpunkte(place + 1);
                }
            }
            this.state = EngineState.FINISHED;
        } else {
            this.state = EngineState.WAITING;
        }
    }

    private void handleWaiting(DartboardInput input) {
        switch (input.type()) {
            case NEXT -> {
                this.soundPublisher.play(Sound.TREFFER);
                this.state = EngineState.RUNNING;
                beginTurn();
                publish();
            }
            case BOUNCE_OUT -> {
                this.soundPublisher.play(Sound.RESET);
                this.game.undoLastTurn();
                publish();
            }
            case THROW -> { /* darts during the NEXT wait are void */ }
        }
    }

    private void handleFinished(DartboardInput input) {
        if (input.type() == DartboardInput.Type.NEXT) {
            this.soundPublisher.play(Sound.TREFFER);
            this.complete = true;
        }
    }

    private void beginTurn() {
        this.turnPlayer = this.byPlayer.get(this.game.getCurrentPlayer());
        this.turnStartScore = scoreOf(this.turnPlayer);
        this.turnSum = 0;
        this.lastThrowValue = 0;
    }

    private int scoreOf(RegisteredPlayer registered) {
        Integer score = this.game.getSnapshot().scores().get(registered.player());
        return score == null ? 0 : score;
    }

    private static Sound soundFor(Segment segment) {
        if (segment == Segment.BULLS_EYE) {
            return Sound.BULLSEYE;
        }
        return switch (segment.getMultiplier()) {
            case 3 -> Sound.TRIPLE;
            case 2 -> Sound.DOUBLE;
            default -> Sound.TREFFER;
        };
    }

    private void publish() {
        this.updatePublisher.publish(new EngineUpdate(
                this.gameId,
                this.totalGames,
                this.penaltyCostCents,
                this.state,
                this.game.getSnapshot(),
                this.turnPlayer,
                this.state == EngineState.RUNNING ? this.game.getSnapshot().throwsLeftInTurn() : 0,
                this.lastThrowValue,
                this.turnSum,
                this.playersInOrder,
                getRanking()
        ));
    }

    public List<RegisteredPlayer> getRanking() {
        List<RegisteredPlayer> ranking = new ArrayList<>();
        for (Player player : this.game.getRanking()) {
            ranking.add(this.byPlayer.get(player));
        }
        return ranking;
    }

    public EngineState getState() {
        return this.state;
    }

    public boolean isComplete() {
        return this.complete;
    }
}
