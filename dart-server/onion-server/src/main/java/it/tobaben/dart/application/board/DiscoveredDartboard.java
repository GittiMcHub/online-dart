package it.tobaben.dart.application.board;

/** A BLE device found during a scan (Smartness boards advertise as "Smartness1"). */
public record DiscoveredDartboard(String mac, String name) {

    /** Heuristic used by the UI to pre-select likely dartboards. */
    public boolean looksLikeDartboard() {
        return this.name != null && this.name.toLowerCase().startsWith("smartness");
    }
}
