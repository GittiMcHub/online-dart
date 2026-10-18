package it.tobaben.dart.application;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.bot.BotProfile;

/**
 * A player as registered for a tournament: domain player plus the dartboard he
 * throws on, his tournament-lifetime statistics and — for bots — the bot
 * profile (null for humans).
 */
public record RegisteredPlayer(int id, Player player, int dartboardId, PlayerStatistics statistics, BotProfile bot) {

    public RegisteredPlayer(int id, Player player, int dartboardId) {
        this(id, player, dartboardId, new PlayerStatistics(), null);
    }

    public RegisteredPlayer(int id, Player player, int dartboardId, BotProfile bot) {
        this(id, player, dartboardId, new PlayerStatistics(), bot);
    }

    public String getName() {
        return this.player.getName();
    }

    public boolean isBot() {
        return this.bot != null;
    }
}
