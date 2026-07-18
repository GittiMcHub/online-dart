package it.tobaben.dart.application.client;

import it.tobaben.dart.application.lobby.LobbyPlayer;

import java.util.List;

/**
 * A client's view of the remote server lobby, parsed from a retained
 * lobby/state message. {@code seq} increases monotonically so stale retained
 * messages (older server run) can be recognized.
 */
public record LobbyStateView(String phase, String serverName, List<LobbyPlayer> players, long seq) {
}
