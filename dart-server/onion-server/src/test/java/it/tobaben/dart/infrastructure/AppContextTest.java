package it.tobaben.dart.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AppContextTest {

    private static final String CLIENT_SESSION_ID = "aabbccdd-1122-3344-5566-778899aabbcc";

    private static MqttConfig config(String clientId) {
        return new MqttConfig("127.0.0.1", 1883, "dartboard", "smartness", clientId, 0);
    }

    @Test
    void clientModeReplacesDefaultServerClientId() {
        MqttConfig result = AppContext.effectiveMqttConfig(AppMode.CLIENT, CLIENT_SESSION_ID, config("server"));
        assertEquals("client-aabbccdd", result.clientId());
    }

    @Test
    void clientModeKeepsExplicitClientId() {
        MqttConfig explicit = config("mein-client");
        assertSame(explicit, AppContext.effectiveMqttConfig(AppMode.CLIENT, CLIENT_SESSION_ID, explicit));
    }

    @Test
    void serverAndCombinedModeKeepDefaultClientId() {
        assertEquals("server", AppContext.effectiveMqttConfig(AppMode.SERVER, CLIENT_SESSION_ID, config("server")).clientId());
        assertEquals("server", AppContext.effectiveMqttConfig(AppMode.COMBINED, CLIENT_SESSION_ID, config("server")).clientId());
    }
}
