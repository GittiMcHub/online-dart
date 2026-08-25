package it.tobaben.dart.infrastructure.ble;

/**
 * Translates the Smartness board's BLE notification byte into the 3-digit wire
 * code — a 1:1 port of hex_mapping in dartBlueMqttConnector.py, so the Java
 * connector emits exactly what the Python connector does:
 * <ul>
 *   <li>0x01-0x14 outer singles, 0x15-0x28 inner singles → "101".."120"</li>
 *   <li>0x29-0x3c doubles → "201".."220"</li>
 *   <li>0x3d-0x50 triples → "301".."320"</li>
 *   <li>0x51 bull → "125", 0x52 bullseye → "225"</li>
 *   <li>0x65 next-player button → "999"</li>
 *   <li>anything unknown → "999" (Python fallback behavior)</li>
 * </ul>
 */
public final class BoardHexCodec {

    public static final String NEXT_PLAYER = "999";

    private BoardHexCodec() {
    }

    /** @param data raw notification payload (the board sends one byte) */
    public static String toWireCode(byte[] data) {
        if (data == null || data.length != 1) {
            return NEXT_PLAYER;
        }
        int value = data[0] & 0xff;
        if (value >= 0x01 && value <= 0x14) {
            return String.valueOf(100 + value); // outer single
        }
        if (value >= 0x15 && value <= 0x28) {
            return String.valueOf(100 + value - 0x14); // inner single
        }
        if (value >= 0x29 && value <= 0x3c) {
            return String.valueOf(200 + value - 0x28); // double
        }
        if (value >= 0x3d && value <= 0x50) {
            return String.valueOf(300 + value - 0x3c); // triple
        }
        if (value == 0x51) {
            return "125"; // bull
        }
        if (value == 0x52) {
            return "225"; // bullseye
        }
        return NEXT_PLAYER; // includes 0x65, the board's next-player button
    }
}
