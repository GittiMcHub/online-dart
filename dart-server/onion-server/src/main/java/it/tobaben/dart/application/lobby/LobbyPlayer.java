package it.tobaben.dart.application.lobby;

import it.tobaben.dart.game.bot.BotProfile;

/**
 * A player waiting in the lobby. Local players are added by the host via the
 * web UI ({@code clientId} = {@link #LOCAL_CLIENT_ID}), remote players join via
 * MQTT with the random clientId of their client instance. Bots are local
 * players with a {@link BotProfile} on a reserved virtual board (900–999).
 */
public record LobbyPlayer(String name, int dartboardId, String clientId, BotProfile bot) {

    public static final String LOCAL_CLIENT_ID = "local";
    public static final int FIRST_BOT_BOARD_ID = 900;
    public static final int LAST_BOT_BOARD_ID = 999;

    public LobbyPlayer(String name, int dartboardId, String clientId) {
        this(name, dartboardId, clientId, null);
    }

    public boolean isLocal() {
        return LOCAL_CLIENT_ID.equals(this.clientId);
    }

    public boolean isBot() {
        return this.bot != null;
    }

    public static boolean isBotBoardId(int dartboardId) {
        return dartboardId >= FIRST_BOT_BOARD_ID && dartboardId <= LAST_BOT_BOARD_ID;
    }
}
