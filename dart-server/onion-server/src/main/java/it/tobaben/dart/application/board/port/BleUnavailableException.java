package it.tobaben.dart.application.board.port;

/**
 * BLE is not usable on this machine (missing native library, no adapter).
 * The message is shown to the user; the Python connector remains the fallback.
 */
public class BleUnavailableException extends RuntimeException {

    public BleUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
