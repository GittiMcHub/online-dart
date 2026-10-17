package it.tobaben.dart.application.lobby;

import it.tobaben.dart.game.bot.BotProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * The lobby: players waiting for the next tournament. Pure application logic,
 * no I/O — MQTT join/leave (M2) and the web UI both call into here.
 *
 * Rules (mirroring the CLI's "--player Alice:1 --player Bob:1" semantics):
 * <ul>
 *   <li>player names are unique across the lobby</li>
 *   <li>several players may share one dartboard — but only from the same
 *       client instance (one physical board belongs to one machine)</li>
 *   <li>a re-join with the same clientId+name just updates the dartboardId
 *       (QoS-1 duplicates and reconnects stay harmless)</li>
 * </ul>
 */
public class LobbyService {

    private final List<LobbyPlayer> players = new ArrayList<>();

    public synchronized LobbyResult join(String name, int dartboardId, String clientId) {
        if (name == null || name.isBlank()) {
            return LobbyResult.failure("Spielername darf nicht leer sein");
        }
        if (dartboardId < 0) {
            return LobbyResult.failure("Dartboard-ID darf nicht negativ sein");
        }
        if (LobbyPlayer.isBotBoardId(dartboardId)) {
            return LobbyResult.failure("Board-IDs " + LobbyPlayer.FIRST_BOT_BOARD_ID + "–"
                    + LobbyPlayer.LAST_BOT_BOARD_ID + " sind für Bots reserviert");
        }
        String trimmed = name.trim();
        for (int i = 0; i < this.players.size(); i++) {
            LobbyPlayer existing = this.players.get(i);
            if (existing.name().equals(trimmed)) {
                if (existing.isBot()) {
                    return LobbyResult.failure("Spielername '" + trimmed + "' ist bereits an einen Bot vergeben");
                }
                if (existing.clientId().equals(clientId)) {
                    this.players.set(i, new LobbyPlayer(trimmed, dartboardId, clientId));
                    return LobbyResult.success();
                }
                return LobbyResult.failure("Spielername '" + trimmed + "' ist bereits vergeben");
            }
        }
        for (LobbyPlayer existing : this.players) {
            if (existing.dartboardId() == dartboardId && !existing.clientId().equals(clientId)) {
                return LobbyResult.failure("Dartboard-ID " + dartboardId
                        + " wird bereits von einem anderen Client verwendet");
            }
        }
        this.players.add(new LobbyPlayer(trimmed, dartboardId, clientId));
        return LobbyResult.success();
    }

    public synchronized LobbyResult leave(String name, String clientId) {
        boolean removed = this.players.removeIf(player ->
                player.name().equals(name) && player.clientId().equals(clientId));
        return removed
                ? LobbyResult.success()
                : LobbyResult.failure("Spieler '" + name + "' ist nicht in der Lobby");
    }

    /** Removes every player of a client instance (MQTT Last-Will, playerName "*"). */
    public synchronized boolean leaveAll(String clientId) {
        return this.players.removeIf(player -> player.clientId().equals(clientId));
    }

    /** Host-side removal by name regardless of owning client (management UI). */
    public synchronized boolean remove(String name) {
        return this.players.removeIf(player -> player.name().equals(name));
    }

    /** Host-side reordering: moves a player by offset positions (clamped). */
    public synchronized LobbyResult move(String name, int offset) {
        int index = -1;
        for (int i = 0; i < this.players.size(); i++) {
            if (this.players.get(i).name().equals(name)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return LobbyResult.failure("Spieler '" + name + "' ist nicht in der Lobby");
        }
        int target = Math.max(0, Math.min(this.players.size() - 1, index + offset));
        this.players.add(target, this.players.remove(index));
        return LobbyResult.success();
    }

    public synchronized void clear() {
        this.players.clear();
    }

    public synchronized List<LobbyPlayer> getPlayers() {
        return List.copyOf(this.players);
    }

    public synchronized boolean isEmpty() {
        return this.players.isEmpty();
    }

    /** Host-side: adds a bot on the next free virtual board (900–999). */
    public synchronized LobbyResult addBot(String name, BotProfile profile) {
        if (name == null || name.isBlank()) {
            return LobbyResult.failure("Spielername darf nicht leer sein");
        }
        String trimmed = name.trim();
        if (this.players.stream().anyMatch(player -> player.name().equals(trimmed))) {
            return LobbyResult.failure("Spielername '" + trimmed + "' ist bereits vergeben");
        }
        for (int id = LobbyPlayer.FIRST_BOT_BOARD_ID; id <= LobbyPlayer.LAST_BOT_BOARD_ID; id++) {
            int candidate = id;
            if (this.players.stream().noneMatch(player -> player.dartboardId() == candidate)) {
                this.players.add(new LobbyPlayer(trimmed, candidate, LobbyPlayer.LOCAL_CLIENT_ID, profile));
                return LobbyResult.success();
            }
        }
        return LobbyResult.failure("Keine freie Bot-Board-ID mehr");
    }
}
