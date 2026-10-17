package it.tobaben.dart.application.bot;

import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.EngineState;
import it.tobaben.dart.application.EngineUpdate;
import it.tobaben.dart.application.GameEngine;
import it.tobaben.dart.application.HouseRules;
import it.tobaben.dart.application.RegisteredPlayer;
import it.tobaben.dart.application.Sound;
import it.tobaben.dart.application.TournamentEngine;
import it.tobaben.dart.application.bot.port.VirtualBoardPort;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.bot.BotProfile;
import it.tobaben.dart.game.x01.X01Game;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

class BotServiceTest {

    static final int BOT_BOARD = 900;
    static final int HUMAN_BOARD = 1;

    /** Runs scheduled tasks only when the test says so. */
    static class ManualScheduler implements BotScheduler {
        static final class Task {
            final Duration delay;
            final Runnable runnable;
            boolean cancelled;

            Task(Duration delay, Runnable runnable) {
                this.delay = delay;
                this.runnable = runnable;
            }
        }

        final List<Task> tasks = new ArrayList<>();

        @Override
        public Cancellable schedule(Duration delay, Runnable task) {
            Task scheduled = new Task(delay, task);
            this.tasks.add(scheduled);
            return () -> scheduled.cancelled = true;
        }

        List<Task> active(boolean watchdogs) {
            return this.tasks.stream()
                    .filter(t -> !t.cancelled)
                    .filter(t -> watchdogs == t.delay.equals(BotTiming.INSTANT.watchdog()))
                    .toList();
        }

        void runNextAction() {
            run(active(false).get(0));
        }

        void runWatchdog() {
            run(active(true).get(0));
        }

        private void run(Task task) {
            this.tasks.remove(task);
            task.runnable.run();
        }
    }

    /** Bot darts land in a queue, like MQTT would deliver them to the server. */
    static class LoopbackBoard implements VirtualBoardPort {
        final BlockingQueue<DartboardInput> queue = new LinkedBlockingQueue<>();
        final List<String> sent = new ArrayList<>();

        @Override
        public void throwDart(int dartboardId, Segment segment) {
            this.sent.add(segment.getCode());
            this.queue.add(DartboardInput.ofThrow(dartboardId, segment));
        }

        @Override
        public void next(int dartboardId) {
            this.sent.add("NEXT");
            this.queue.add(DartboardInput.next(dartboardId));
        }
    }

    static class RecordingPorts implements GameUpdatePublisherPort, SoundPublisherPort {
        EngineUpdate last;

        @Override
        public void publish(EngineUpdate update) {
            this.last = update;
        }

        @Override
        public void play(Sound sound) {
        }
    }

    final ManualScheduler scheduler = new ManualScheduler();
    final LoopbackBoard board = new LoopbackBoard();
    final RecordingPorts ports = new RecordingPorts();
    final BotService bots = new BotService(board, BotTiming.INSTANT, scheduler, new Random(1));
    final GameUpdatePublisherPort observed = bots.observing(ports);

    final RegisteredPlayer bot = new RegisteredPlayer(0, new Player("Bot"), BOT_BOARD, new BotProfile(180));
    final RegisteredPlayer human = new RegisteredPlayer(1, new Player("Alice"), HUMAN_BOARD);

    private GameEngine startGame(List<RegisteredPlayer> players) {
        bots.startTournament(Map.of(BOT_BOARD, new BotProfile(180)), HouseRules.allOff());
        GameEngine engine = new GameEngine(new X01Game(301, false, false), players,
                1, 1, 0, HouseRules.allOff(), observed, ports);
        engine.start();
        return engine;
    }

    /** Runs the next bot action and feeds the resulting input into the engine. */
    private void step(GameEngine engine) {
        scheduler.runNextAction();
        DartboardInput input = board.queue.poll();
        assertNotNull(input, "bot sent nothing");
        engine.handle(input);
    }

    @Test
    void botThrowsThreeDartsThenPullsThem() {
        GameEngine engine = startGame(List.of(bot, human));
        step(engine);
        step(engine);
        step(engine);
        assertEquals(EngineState.WAITING, ports.last.state());
        step(engine);

        assertEquals(List.of("T20", "T20", "T20", "NEXT"), board.sent);
        assertEquals(human, ports.last.currentPlayer());
        assertEquals(EngineState.RUNNING, ports.last.state());
        assertTrue(scheduler.active(false).isEmpty(), "nothing scheduled during a human turn");
    }

    @Test
    void soloBotKeepsPlayingAcrossTurns() {
        GameEngine engine = startGame(List.of(bot));
        for (int i = 0; i < 8; i++) { // two turns of 3 darts + NEXT each
            step(engine);
        }
        // 301: T20 T20 T20 (121), NEXT; 121: T20 (61), T20 (1), S1 = finished; NEXT completes the game
        assertEquals(List.of("T20", "T20", "T20", "NEXT", "T20", "T20", "S1", "NEXT"), board.sent);
    }

    @Test
    void humanNextCancelsPendingBotNext() {
        GameEngine engine = startGame(List.of(bot, human));
        step(engine);
        step(engine);
        step(engine);
        assertEquals(1, scheduler.active(false).size()); // pending NEXT

        engine.handle(DartboardInput.next(HUMAN_BOARD)); // in WAITING any board may continue

        assertTrue(scheduler.active(false).isEmpty());
        assertFalse(board.sent.contains("NEXT"));
    }

    @Test
    void undoInWaitingKeepsSingleNext() {
        GameEngine engine = startGame(List.of(bot, human));
        step(engine);
        step(engine);
        step(engine);
        engine.handle(DartboardInput.bounceOut(HUMAN_BOARD)); // undo the bot's turn

        assertEquals(1, scheduler.active(false).size());
        step(engine);
        assertEquals(1, board.sent.stream().filter("NEXT"::equals).count());
        assertEquals(human, ports.last.currentPlayer());
    }

    @Test
    void bounceOutOnBotBoardBeforeFirstDartStillPullsDarts() {
        GameEngine engine = startGame(List.of(bot));
        for (int i = 0; i < 4; i++) { // turn 1: 3 darts + NEXT
            step(engine);
        }
        // turn 2, dart pending: the TV "Bounce Out" control hits the bot's board
        engine.handle(DartboardInput.bounceOut(BOT_BOARD));
        assertEquals(EngineState.WAITING, ports.last.state());

        step(engine);

        assertEquals("NEXT", board.sent.get(board.sent.size() - 1));
        assertEquals(EngineState.RUNNING, ports.last.state());
    }

    @Test
    void duplicateUpdateDoesNotScheduleTwice() {
        startGame(List.of(bot, human));
        observed.publish(ports.last);
        assertEquals(1, scheduler.active(false).size());
    }

    @Test
    void watchdogResendsWhenThrowGetsLost() {
        startGame(List.of(bot, human));
        scheduler.runNextAction();
        board.queue.clear(); // broker lost the dart: no update follows

        scheduler.runWatchdog();

        assertEquals(List.of("T20", "T20"), board.sent);
    }

    @Test
    void stopTournamentCancelsEverything() {
        startGame(List.of(bot, human));
        bots.stopTournament();
        assertTrue(scheduler.active(false).isEmpty());
        assertTrue(scheduler.active(true).isEmpty());
    }

    @Test
    void twoPerfectBotsPlayATournamentToTheEnd() {
        BlockingQueue<DartboardInput> queue = new LinkedBlockingQueue<>();
        VirtualBoardPort loopback = new VirtualBoardPort() {
            @Override
            public void throwDart(int dartboardId, Segment segment) {
                queue.add(DartboardInput.ofThrow(dartboardId, segment));
            }

            @Override
            public void next(int dartboardId) {
                queue.add(DartboardInput.next(dartboardId));
            }
        };
        BotService realBots = new BotService(loopback, BotTiming.INSTANT, new ExecutorBotScheduler(), new Random(1));
        RegisteredPlayer first = new RegisteredPlayer(0, new Player("Bot 1"), 900, new BotProfile(180));
        RegisteredPlayer second = new RegisteredPlayer(1, new Player("Bot 2"), 901, new BotProfile(180));
        realBots.startTournament(Map.of(900, new BotProfile(180), 901, new BotProfile(180)), HouseRules.allOff());
        TournamentEngine tournament = new TournamentEngine(queue, List.of(first, second),
                () -> new X01Game(301, false, false), 1, 0, HouseRules.allOff(),
                realBots.observing(ports), ports);

        assertTimeoutPreemptively(Duration.ofSeconds(10), tournament::runTournament);

        // 180, then 121 = T20 T20 S1: the starting bot always wins
        assertEquals(first, tournament.getGameResults().get(0).get(0));
        realBots.stopTournament();
    }
}
