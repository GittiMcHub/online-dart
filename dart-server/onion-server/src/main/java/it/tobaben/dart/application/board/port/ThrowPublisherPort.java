package it.tobaben.dart.application.board.port;

/**
 * Outbound port: forwards a board's wire code to the game. The infrastructure
 * publishes it on dartboard/&lt;id&gt; (QoS 2) — always via the broker, also in
 * combined mode, so there is exactly one input path into the server.
 */
public interface ThrowPublisherPort {

    void publishThrow(int dartboardId, String wireCode);
}
