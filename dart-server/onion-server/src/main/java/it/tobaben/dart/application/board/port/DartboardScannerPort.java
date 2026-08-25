package it.tobaben.dart.application.board.port;

import it.tobaben.dart.application.board.DiscoveredDartboard;

import java.time.Duration;
import java.util.List;

/**
 * Outbound port: scans for BLE dartboards. Implemented by SimpleJavaBLE in the
 * infrastructure; a fake suffices for tests, and machines without BLE support
 * throw {@link BleUnavailableException} on first use.
 */
public interface DartboardScannerPort {

    /** Blocking scan; call from a worker thread. */
    List<DiscoveredDartboard> scan(Duration duration);
}
