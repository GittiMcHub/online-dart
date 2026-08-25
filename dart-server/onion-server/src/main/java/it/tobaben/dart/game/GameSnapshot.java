package it.tobaben.dart.game;

import it.tobaben.dart.common.Player;

import java.util.List;
import java.util.Map;

/**
 * Mode-agnostic view of a running game. Outer layers (e.g. the AnthraxJsonMapper)
 * render this into client-facing formats; the domain never deals with JSON.
 *
 * @param gameMode          identifier of the game mode, e.g. "X01" or "CRICKET"
 * @param playerOrder       all players in turn order
 * @param currentPlayer     the player whose turn it is (null before the first player joined)
 * @param scores            main score per player (X01: remaining points, Cricket: points)
 * @param ranking           finished players so far, winner first (complete when over)
 * @param over              whether the game is over
 * @param throwsLeftInTurn  darts the current player may still throw in the open turn
 * @param lastThrowScore    face value of the last evaluated dart (0 if none yet)
 * @param turnScore         thrown sum of the open turn (last finished turn if none open)
 * @param modeData          mode-specific extras as flat string map; Cricket uses
 *                          "marks:&lt;playerName&gt;:&lt;number&gt;" -> mark count,
 *                          X01 uses "startScore", "doubleIn", "doubleOut"
 */
public record GameSnapshot(
        String gameMode,
        List<Player> playerOrder,
        Player currentPlayer,
        Map<Player, Integer> scores,
        List<Player> ranking,
        boolean over,
        int throwsLeftInTurn,
        int lastThrowScore,
        int turnScore,
        Map<String, String> modeData
) {
}
