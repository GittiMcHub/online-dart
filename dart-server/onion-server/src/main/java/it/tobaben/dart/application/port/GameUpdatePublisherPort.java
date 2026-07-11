package it.tobaben.dart.application.port;

import it.tobaben.dart.application.EngineUpdate;

/**
 * Outbound port: publishes the game state to the clients
 * (infrastructure maps it to status/gameUpdate JSON).
 */
public interface GameUpdatePublisherPort {
    void publish(EngineUpdate update);
}
