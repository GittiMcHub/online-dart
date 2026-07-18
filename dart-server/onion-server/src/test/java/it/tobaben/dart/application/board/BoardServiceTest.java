package it.tobaben.dart.application.board;

import it.tobaben.dart.application.board.port.BleUnavailableException;
import it.tobaben.dart.application.board.port.DartboardConnectionPort;
import it.tobaben.dart.application.board.port.DartboardScannerPort;
import it.tobaben.dart.application.lobby.LobbyResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class BoardServiceTest {

    static class FakeConnection implements DartboardConnectionPort {
        Consumer<String> onWireCode;
        Consumer<BoardStatus> onStatus;
        boolean closed;

        @Override
        public void open(Consumer<String> onWireCode, Consumer<BoardStatus> onStatus) {
            this.onWireCode = onWireCode;
            this.onStatus = onStatus;
            onStatus.accept(BoardStatus.CONNECTED);
        }

        @Override
        public void close() {
            this.closed = true;
        }
    }

    record PublishedThrow(int dartboardId, String code) {
    }

    List<PublishedThrow> published = new ArrayList<>();
    FakeConnection connection = new FakeConnection();
    DartboardScannerPort scanner = duration -> List.of(
            new DiscoveredDartboard("cc:33:31:c1:27:a3", "Smartness1"),
            new DiscoveredDartboard("aa:bb:cc:dd:ee:ff", ""));
    BoardService service = new BoardService(
            () -> scanner,
            mac -> connection,
            (dartboardId, code) -> published.add(new PublishedThrow(dartboardId, code)));

    private void awaitScanDone() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (service.isScanning()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Scan wurde nicht fertig");
            }
            Thread.sleep(10);
        }
    }

    @Test
    void scanCollectsBoards() throws InterruptedException {
        assertTrue(service.startScan().ok());
        awaitScanDone();
        assertEquals(2, service.getLastScan().size());
        assertTrue(service.getLastScan().get(0).looksLikeDartboard());
        assertFalse(service.getLastScan().get(1).looksLikeDartboard());
        assertNull(service.getLastError());
    }

    @Test
    void connectForwardsThrowsToPublisher() {
        assertTrue(service.connect("CC:33:31:C1:27:A3", 2).ok());
        assertEquals(1, service.getBoards().size());
        assertEquals(BoardStatus.CONNECTED, service.getBoards().get(0).status());

        connection.onWireCode.accept("320");
        connection.onWireCode.accept("999");
        assertEquals(List.of(
                new PublishedThrow(2, "320"),
                new PublishedThrow(2, "999")), published);
    }

    @Test
    void duplicateConnectIsRejected() {
        service.connect("cc:33:31:c1:27:a3", 1);
        LobbyResult second = service.connect("CC:33:31:C1:27:A3", 2);
        assertFalse(second.ok());
        assertTrue(second.error().contains("bereits verbunden"));
    }

    @Test
    void disconnectClosesConnection() {
        service.connect("cc:33:31:c1:27:a3", 1);
        assertTrue(service.disconnect("cc:33:31:c1:27:a3").ok());
        assertTrue(connection.closed);
        assertTrue(service.getBoards().isEmpty());
        assertFalse(service.disconnect("cc:33:31:c1:27:a3").ok());
    }

    @Test
    void bleUnavailableSurfacesAsError() throws InterruptedException {
        BoardService unavailable = new BoardService(
                () -> {
                    throw new BleUnavailableException("BLE ist auf dieser Plattform nicht verfügbar", null);
                },
                mac -> connection,
                (dartboardId, code) -> {
                });
        assertTrue(unavailable.startScan().ok());
        long deadline = System.currentTimeMillis() + 5000;
        while (unavailable.isScanning() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNotNull(unavailable.getLastError());
        assertTrue(unavailable.getLastError().contains("nicht verfügbar"));
    }

    @Test
    void connectedBoardStaysVisibleAfterScanWithoutIt() throws InterruptedException {
        service.connect("11:22:33:44:55:66", 1);
        service.startScan();
        awaitScanDone();
        assertTrue(service.getLastScan().stream()
                .anyMatch(board -> board.mac().equals("11:22:33:44:55:66")));
    }

    @Test
    void shutdownClosesEverything() {
        service.connect("cc:33:31:c1:27:a3", 1);
        service.shutdown();
        assertTrue(connection.closed);
        assertTrue(service.getBoards().isEmpty());
    }
}
