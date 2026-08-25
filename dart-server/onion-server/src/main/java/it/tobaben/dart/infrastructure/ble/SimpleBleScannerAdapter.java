package it.tobaben.dart.infrastructure.ble;

import it.tobaben.dart.application.board.DiscoveredDartboard;
import it.tobaben.dart.application.board.port.BleUnavailableException;
import it.tobaben.dart.application.board.port.DartboardScannerPort;
import org.simplejavable.Adapter;
import org.simplejavable.Peripheral;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * BLE scan via SimpleJavaBLE (vendored jar under libs/, see libs/README.md).
 * All SimpleJavaBLE access is isolated in this package; loading failures
 * (missing native library on this OS/arch, no Bluetooth adapter) surface as
 * BleUnavailableException with a German message.
 */
public class SimpleBleScannerAdapter implements DartboardScannerPort {

    public static final String BLE_UNAVAILABLE =
            "BLE ist auf dieser Plattform nicht verfügbar – bitte den Python-Connector verwenden";

    @Override
    public List<DiscoveredDartboard> scan(Duration duration) {
        Adapter adapter = firstAdapter();
        try {
            adapter.scanFor((int) duration.toMillis());
            List<DiscoveredDartboard> found = new ArrayList<>();
            for (Peripheral peripheral : adapter.scanGetResults()) {
                found.add(new DiscoveredDartboard(
                        peripheral.getAddress().toString().toLowerCase(),
                        peripheral.getIdentifier()));
            }
            return found;
        } catch (Exception e) {
            throw new RuntimeException("BLE-Scan fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    static Adapter firstAdapter() {
        List<Adapter> adapters;
        try {
            adapters = Adapter.getAdapters();
        } catch (Throwable t) { // includes UnsatisfiedLinkError/NoClassDefFoundError
            throw new BleUnavailableException(BLE_UNAVAILABLE + " (" + t.getMessage() + ")", t);
        }
        if (adapters.isEmpty()) {
            throw new BleUnavailableException("Kein Bluetooth-Adapter gefunden", null);
        }
        return adapters.get(0);
    }

    /** Finds a peripheral by MAC with a short scan (used when connecting). */
    static Peripheral findPeripheral(String mac, int scanMillis) throws Exception {
        Adapter adapter = firstAdapter();
        adapter.scanFor(scanMillis);
        for (Peripheral peripheral : adapter.scanGetResults()) {
            if (peripheral.getAddress().toString().equalsIgnoreCase(mac)) {
                return peripheral;
            }
        }
        return null;
    }
}
