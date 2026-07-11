package it.tobaben.dart.application;

import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.game.DartLikeGame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

/**
 * Runs a tournament of N games over one input queue. Like anthrax: the first
 * game uses the registration order, every following game is started by the
 * previous game's ranking in reverse (last place throws first); per-game
 * statistics are reset between games, tournament statistics accumulate.
 */
public class TournamentEngine {

    private final BlockingQueue<DartboardInput> inputQueue;
    private final List<RegisteredPlayer> players;
    private final Supplier<DartLikeGame> gameFactory;
    private final int totalGames;
    private final int penaltyCostCents;
    private final HouseRules houseRules;
    private final GameUpdatePublisherPort updatePublisher;
    private final SoundPublisherPort soundPublisher;

    private final List<List<RegisteredPlayer>> gameResults = new ArrayList<>();

    public TournamentEngine(BlockingQueue<DartboardInput> inputQueue,
                            List<RegisteredPlayer> players,
                            Supplier<DartLikeGame> gameFactory,
                            int totalGames, int penaltyCostCents, HouseRules houseRules,
                            GameUpdatePublisherPort updatePublisher, SoundPublisherPort soundPublisher) {
        if (totalGames <= 0) {
            throw new IllegalArgumentException("totalGames must be > 0");
        }
        if (players.isEmpty()) {
            throw new IllegalArgumentException("players must not be empty");
        }
        this.inputQueue = inputQueue;
        this.players = List.copyOf(players);
        this.gameFactory = gameFactory;
        this.totalGames = totalGames;
        this.penaltyCostCents = penaltyCostCents;
        this.houseRules = houseRules;
        this.updatePublisher = updatePublisher;
        this.soundPublisher = soundPublisher;
    }

    /** Plays all games of the tournament, blocking on the input queue. */
    public void runTournament() throws InterruptedException {
        for (int gameId = 1; gameId <= this.totalGames; gameId++) {
            List<RegisteredPlayer> order = nextGameOrder();
            for (RegisteredPlayer registered : this.players) {
                registered.statistics().resetGameData();
            }
            GameEngine engine = new GameEngine(
                    this.gameFactory.get(), order,
                    gameId, this.totalGames, this.penaltyCostCents, this.houseRules,
                    this.updatePublisher, this.soundPublisher);
            engine.start();
            while (!engine.isComplete()) {
                engine.handle(this.inputQueue.take());
            }
            this.gameResults.add(engine.getRanking());
        }
    }

    private List<RegisteredPlayer> nextGameOrder() {
        if (this.gameResults.isEmpty()) {
            return this.players;
        }
        List<RegisteredPlayer> order = new ArrayList<>(this.gameResults.get(this.gameResults.size() - 1));
        Collections.reverse(order);
        return order;
    }

    /** @return the final ranking of every finished game, in played order */
    public List<List<RegisteredPlayer>> getGameResults() {
        return List.copyOf(this.gameResults);
    }
}
