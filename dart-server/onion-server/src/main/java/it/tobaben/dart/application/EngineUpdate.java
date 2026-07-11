package it.tobaben.dart.application;

import it.tobaben.dart.game.GameSnapshot;

import java.util.List;

/**
 * Everything an outbound publisher needs to render a status/gameUpdate message.
 * Semantics follow anthrax: during WAITING/FINISHED the current player is the
 * player who just played (freieWuerfe 0), turnSum keeps the thrown sum of the
 * turn even after a bust or abort.
 *
 * @param gameId            1-based number of the game within the tournament
 * @param totalGames        number of games in the tournament
 * @param penaltyCostCents  cost of one penalty point in cents
 * @param state             engine state (RUNNING/WAITING/FINISHED)
 * @param snapshot          domain view of the game
 * @param currentPlayer     player at the board (turn owner during WAITING)
 * @param freieWuerfe       darts the current player may still throw (0 outside RUNNING)
 * @param lastThrowValue    value of the last evaluated dart (0 for miss/wall hit)
 * @param turnSum           sum thrown in the current/last turn
 * @param playersInOrder    turn order of the current game
 * @param ranking           finished players so far, winner first
 */
public record EngineUpdate(
        int gameId,
        int totalGames,
        int penaltyCostCents,
        EngineState state,
        GameSnapshot snapshot,
        RegisteredPlayer currentPlayer,
        int freieWuerfe,
        int lastThrowValue,
        int turnSum,
        List<RegisteredPlayer> playersInOrder,
        List<RegisteredPlayer> ranking
) {
}
