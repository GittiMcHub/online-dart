package it.tobaben.dart.infrastructure.ble;

import it.tobaben.dart.application.board.BoardStatus;
import it.tobaben.dart.application.board.port.DartboardConnectionPort;
import org.simplejavable.BluetoothUUID;
import org.simplejavable.Peripheral;

import java.util.function.Consumer;

/**
 * One dartboard connection via SimpleJavaBLE: subscribes to the Smartness
 * notify characteristic and reconnects with a 5s backoff until closed. Runs
 * its own daemon thread; wire codes leave via the onWireCode consumer.
 */
public class SimpleBleBoardConnection implements DartboardConnectionPort {

    public static final String SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String NOTIFY_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    static final long RECONNECT_BACKOFF_MILLIS = 5000;
    static final int FIND_SCAN_MILLIS = 6000;

    private final String mac;
    private volatile boolean closed;
    private volatile Peripheral peripheral;
    private Thread worker;

    public SimpleBleBoardConnection(String mac) {
        this.mac = mac;
    }

    @Override
    public synchronized void open(Consumer<String> onWireCode, Consumer<BoardStatus> onStatus) {
        this.worker = new Thread(() -> run(onWireCode, onStatus), "ble-" + this.mac);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    private void run(Consumer<String> onWireCode, Consumer<BoardStatus> onStatus) {
        boolean firstAttempt = true;
        while (!this.closed) {
            onStatus.accept(firstAttempt ? BoardStatus.CONNECTING : BoardStatus.RECONNECTING);
            firstAttempt = false;
            try {
                Peripheral found = SimpleBleScannerAdapter.findPeripheral(this.mac, FIND_SCAN_MILLIS);
                if (found == null) {
                    throw new IllegalStateException("Board nicht gefunden");
                }
                found.connect();
                this.peripheral = found;
                found.notify(new BluetoothUUID(SERVICE_UUID), new BluetoothUUID(NOTIFY_UUID),
                        data -> onWireCode.accept(BoardHexCodec.toWireCode(data)));
                onStatus.accept(BoardStatus.CONNECTED);
                while (!this.closed && found.isConnected()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception | UnsatisfiedLinkError e) {
                System.err.println("[BLE] " + this.mac + ": " + e.getMessage());
            }
            if (!this.closed) {
                try {
                    Thread.sleep(RECONNECT_BACKOFF_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        disconnectQuietly();
        onStatus.accept(BoardStatus.DISCONNECTED);
    }

    @Override
    public synchronized void close() {
        this.closed = true;
        if (this.worker != null) {
            this.worker.interrupt();
        }
    }

    private void disconnectQuietly() {
        Peripheral connected = this.peripheral;
        if (connected != null) {
            try {
                connected.disconnect();
            } catch (Exception e) {
                // board already gone
            }
        }
    }
}
