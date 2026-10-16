package it.tobaben.dart.application.bot;

import it.tobaben.dart.application.EngineState;
import it.tobaben.dart.application.EngineUpdate;
import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.application.bot.port.VirtualBoardPort;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.bot.BotProfile;
import it.tobaben.dart.game.bot.BotStrategy;
import it.tobaben.dart.game.bot.CricketStrategy;
import it.tobaben.dart.game.bot.ThrowModel;
import it.tobaben.dart.game.bot.X01Strategy;
import it.tobaben.dart.game.cricket.CricketGame;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Plays the bots of the running tournament. Sees every EngineUpdate (decorator
 * around the GameUpdatePublisherPort) and answers with darts on the bot's
 * virtual board. The latest update is the only truth: at most one action is
 * pending, keyed by the game state it was planned for; any other state cancels
 * it, so undo, aborted turns or a human pressing NEXT never cause extra darts.
 *
 * Threading: publish() runs on the tournament thread, actions on the
 * scheduler thread; all state is guarded by this.
 */
public class BotService {

    record ActionKey(int gameId, int playerId, int freieWuerfe, EngineState state) {
        static ActionKey of(EngineUpdate update) {
            return new ActionKey(update.gameId(), update.currentPlayer().id(),
                    update.freieWuerfe(), update.state());
        }
    }

    private record Bot(ThrowModel throwModel, BotStrategy x01, BotStrategy cricket) {
    }

    private final VirtualBoardPort board;
    private final BotTiming timing;
    private final BotScheduler scheduler;
    private final Random random;

    private Map<Integer, Bot> bots = Map.of();
    private EngineUpdate latest;
    private ActionKey latestKey;
    private ActionKey pendingKey;
    private BotScheduler.Cancellable pending;
    private ActionKey lastExecutedKey;
    private BotScheduler.Cancellable watchdog;

    public BotService(VirtualBoardPort board, BotTiming timing, BotScheduler scheduler, Random random) {
        this.board = board;
        this.timing = timing;
        this.scheduler = scheduler;
        this.random = random;
    }

    /** @return a publisher that forwards everything to delegate and lets the bots react */
    public GameUpdatePublisherPort observing(GameUpdatePublisherPort delegate) {
        return new GameUpdatePublisherPort() {
            @Override
            public void publish(EngineUpdate update) {
                delegate.publish(update);
                onUpdate(update);
            }

            @Override
            public void publishIdle() {
                delegate.publishIdle();
            }
        };
    }

    public synchronized void startTournament(Map<Integer, BotProfile> botsByBoard, HouseRules houseRules) {
        stopTournament();
        IntPredicate penalized = houseRules.schnapszahlPenalty() ? HouseRules::isSchnapszahl : rest -> false;
        Map<Integer, Bot> created = new HashMap<>();
        botsByBoard.forEach((boardId, profile) -> {
            ThrowModel model = new ThrowModel(profile.sigma(), new Random(this.random.nextLong()));
            created.put(boardId, new Bot(model,
                    new X01Strategy(profile.smart(), model, penalized),
                    new CricketStrategy(profile.smart())));
        });
        this.bots = Map.copyOf(created);
    }

    public synchronized void stopTournament() {
        cancelPending();
        cancelWatchdog();
        this.bots = Map.of();
        this.latest = null;
        this.latestKey = null;
        this.lastExecutedKey = null;
    }

    synchronized void onUpdate(EngineUpdate update) {
        if (update.currentPlayer() == null) {
            return;
        }
        this.latest = update;
        ActionKey key = ActionKey.of(update);
        if (key.equals(this.latestKey)) {
            return; // same state again (duplicate, undo in WAITING): keep what is planned
        }
        this.latestKey = key;
        cancelPending();
        cancelWatchdog();
        this.lastExecutedKey = null; // the state moved on, a new action is due
        if (!this.bots.containsKey(update.currentPlayer().dartboardId())) {
            return;
        }
        Duration delay = delayFor(update.state());
        if (delay == null) {
            return;
        }
        this.pendingKey = key;
        this.pending = this.scheduler.schedule(delay, () -> execute(key));
    }

    private Duration delayFor(EngineState state) {
        return switch (state) {
            case RUNNING -> {
                long spread = this.timing.maxDart().toMillis() - this.timing.minDart().toMillis();
                yield this.timing.minDart().plusMillis((long) (this.random.nextDouble() * spread));
            }
            case WAITING -> this.timing.nextDelay();
            case FINISHED -> this.timing.gameEndDelay();
            default -> null;
        };
    }

    private synchronized void execute(ActionKey key) {
        if (!key.equals(this.pendingKey) && !key.equals(this.lastExecutedKey)) {
            return; // superseded
        }
        this.pending = null;
        this.pendingKey = null;
        EngineUpdate update = this.latest;
        if (update == null || !ActionKey.of(update).equals(key)) {
            return;
        }
        int boardId = update.currentPlayer().dartboardId();
        Bot bot = this.bots.get(boardId);
        if (bot == null) {
            return;
        }
        try {
            if (update.state() == EngineState.RUNNING) {
                BotStrategy strategy = CricketGame.GAME_MODE.equals(update.snapshot().gameMode())
                        ? bot.cricket() : bot.x01();
                Segment hit = bot.throwModel().throwAt(
                        strategy.aim(update.snapshot(), update.currentPlayer().player()));
                this.board.throwDart(boardId, hit);
            } else {
                this.board.next(boardId);
            }
        } catch (RuntimeException e) {
            System.err.println("[BOT] Aktion fehlgeschlagen, Watchdog wiederholt: " + e.getMessage());
        }
        this.lastExecutedKey = key;
        cancelWatchdog();
        this.watchdog = this.scheduler.schedule(this.timing.watchdog(), () -> execute(key));
    }

    private void cancelPending() {
        if (this.pending != null) {
            this.pending.cancel();
        }
        this.pending = null;
        this.pendingKey = null;
    }

    private void cancelWatchdog() {
        if (this.watchdog != null) {
            this.watchdog.cancel();
        }
        this.watchdog = null;
    }
}
