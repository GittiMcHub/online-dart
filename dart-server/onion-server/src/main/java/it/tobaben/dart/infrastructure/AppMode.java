package it.tobaben.dart.infrastructure;

/**
 * Operating mode of the long-lived application:
 * <ul>
 *   <li>SERVER — hosts lobby, tournament and web display; no local dartboards</li>
 *   <li>CLIENT — discovers local dartboards and joins a remote server's lobby</li>
 *   <li>COMBINED — both at once (the host also plays locally)</li>
 * </ul>
 */
public enum AppMode {
    SERVER,
    CLIENT,
    COMBINED;

    public static AppMode parse(String value) {
        try {
            return AppMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unbekannter Modus: " + value + " (erwartet: server, client oder combined)");
        }
    }

    public boolean actsAsServer() {
        return this != CLIENT;
    }

    public boolean actsAsClient() {
        return this != SERVER;
    }
}
