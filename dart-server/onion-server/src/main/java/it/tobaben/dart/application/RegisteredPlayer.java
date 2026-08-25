package it.tobaben.dart.application;

import it.tobaben.dart.common.Player;

/**
 * A player as registered for a tournament: domain player plus the dartboard he
 * throws on and his tournament-lifetime statistics.
 */
public record RegisteredPlayer(int id, Player player, int dartboardId, PlayerStatistics statistics) {

    public RegisteredPlayer(int id, Player player, int dartboardId) {
        this(id, player, dartboardId, new PlayerStatistics());
    }

    public String getName() {
        return this.player.getName();
    }
}
