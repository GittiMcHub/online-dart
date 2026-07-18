package it.tobaben.dart.application.board;

import it.tobaben.dart.application.board.port.BleUnavailableException;
import it.tobaben.dart.application.board.port.DartboardConnectionPort;
import it.tobaben.dart.application.board.port.DartboardScannerPort;
import it.tobaben.dart.application.board.port.ThrowPublisherPort;
import it.tobaben.dart.application.lobby.LobbyResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client-mode board management: scan for dartboards, connect them to a
 * dartboard id and forward their throws to the game via the throw publisher.
 * The BLE implementation stays behind ports/factories so it is testable and
 * replaceable (Python connector remains a valid alternative path).
 */
public class BoardService {

    public static final Duration SCAN_DURATION = Duration.ofSeconds(8);

    /** One managed board: connection plus UI-facing state. */
    public static final class ManagedBoard {
        private final String mac;
        private final String name;
        private final int dartboardId;
        private final DartboardConnectionPort connection;
        private volatile BoardStatus status = BoardStatus.CONNECTING;

        private ManagedBoard(String mac, String name, int dartboardId, DartboardConnectionPort connection) {
            this.mac = mac;
            this.name = name;
            this.dartboardId = dartboardId;
            this.connection = connection;
        }

        public String mac() {
            return this.mac;
        }

        public String name() {
            return this.name;
        }

        public int dartboardId() {
            return this.dartboardId;
        }

        public BoardStatus status() {
            return this.status;
        }
    }

    private final Supplier<DartboardScannerPort> scannerFactory;
    private final Function<String, DartboardConnectionPort> connectionFactory;
    private final ThrowPublisherPort throwPublisher;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private final Map<String, ManagedBoard> boards = new LinkedHashMap<>();
    private volatile List<DiscoveredDartboard> lastScan = List.of();
    private volatile boolean scanning;
    private volatile String lastError;

    public BoardService(Supplier<DartboardScannerPort> scannerFactory,
                        Function<String, DartboardConnectionPort> connectionFactory,
                        ThrowPublisherPort throwPublisher) {
        this.scannerFactory = scannerFactory;
        this.connectionFactory = connectionFactory;
        this.throwPublisher = throwPublisher;
    }

    public void addChangeListener(Runnable listener) {
        this.changeListeners.add(listener);
    }

    /** Starts an async scan; result lands in {@link #getLastScan()} via change event. */
    public synchronized LobbyResult startScan() {
        if (this.scanning) {
            return LobbyResult.failure("Scan läuft bereits");
        }
        this.scanning = true;
        this.lastError = null;
        fireChange();
        Thread worker = new Thread(this::runScan, "ble-scan");
        worker.setDaemon(true);
        worker.start();
        return LobbyResult.success();
    }

    private void runScan() {
        try {
            List<DiscoveredDartboard> found = this.scannerFactory.get().scan(SCAN_DURATION);
            // boards we already manage should stay visible even when asleep
            List<DiscoveredDartboard> merged = new ArrayList<>(found);
            synchronized (this) {
                for (ManagedBoard board : this.boards.values()) {
                    if (found.stream().noneMatch(d -> d.mac().equalsIgnoreCase(board.mac()))) {
                        merged.add(new DiscoveredDartboard(board.mac(), board.name()));
                    }
                }
                this.lastScan = List.copyOf(merged);
            }
        } catch (BleUnavailableException e) {
            this.lastError = e.getMessage();
        } catch (RuntimeException e) {
            this.lastError = "Scan fehlgeschlagen: " + e.getMessage();
        } finally {
            this.scanning = false;
            fireChange();
        }
    }

    public synchronized LobbyResult connect(String mac, int dartboardId) {
        if (this.boards.containsKey(mac.toLowerCase())) {
            return LobbyResult.failure("Board " + mac + " ist bereits verbunden");
        }
        if (dartboardId < 0) {
            return LobbyResult.failure("Dartboard-ID darf nicht negativ sein");
        }
        String name = this.lastScan.stream()
                .filter(d -> d.mac().equalsIgnoreCase(mac))
                .map(DiscoveredDartboard::name)
                .findFirst().orElse("");
        DartboardConnectionPort connection;
        try {
            connection = this.connectionFactory.apply(mac);
        } catch (BleUnavailableException e) {
            return LobbyResult.failure(e.getMessage());
        }
        ManagedBoard board = new ManagedBoard(mac.toLowerCase(), name, dartboardId, connection);
        this.boards.put(board.mac(), board);
        connection.open(
                code -> this.throwPublisher.publishThrow(dartboardId, code),
                status -> {
                    board.status = status;
                    fireChange();
                });
        fireChange();
        return LobbyResult.success();
    }

    /**
     * Tears the connection down and rebuilds it with the same dartboard id —
     * for boards that look connected but silently stopped notifying.
     */
    public synchronized LobbyResult reconnect(String mac) {
        ManagedBoard board = this.boards.get(mac.toLowerCase());
        if (board == null) {
            return LobbyResult.failure("Board " + mac + " ist nicht verbunden");
        }
        board.connection.close();
        DartboardConnectionPort connection;
        try {
            connection = this.connectionFactory.apply(board.mac());
        } catch (BleUnavailableException e) {
            this.boards.remove(board.mac());
            fireChange();
            return LobbyResult.failure(e.getMessage());
        }
        ManagedBoard fresh = new ManagedBoard(board.mac(), board.name(), board.dartboardId(), connection);
        this.boards.put(fresh.mac(), fresh);
        int dartboardId = fresh.dartboardId();
        connection.open(
                code -> this.throwPublisher.publishThrow(dartboardId, code),
                status -> {
                    fresh.status = status;
                    fireChange();
                });
        fireChange();
        return LobbyResult.success();
    }

    public synchronized LobbyResult disconnect(String mac) {
        ManagedBoard board = this.boards.remove(mac.toLowerCase());
        if (board == null) {
            return LobbyResult.failure("Board " + mac + " ist nicht verbunden");
        }
        board.connection.close();
        fireChange();
        return LobbyResult.success();
    }

    public synchronized List<ManagedBoard> getBoards() {
        return List.copyOf(this.boards.values());
    }

    public List<DiscoveredDartboard> getLastScan() {
        return this.lastScan;
    }

    public boolean isScanning() {
        return this.scanning;
    }

    public String getLastError() {
        return this.lastError;
    }

    public synchronized void shutdown() {
        for (ManagedBoard board : this.boards.values()) {
            board.connection.close();
        }
        this.boards.clear();
    }

    private void fireChange() {
        for (Runnable listener : this.changeListeners) {
            listener.run();
        }
    }
}
