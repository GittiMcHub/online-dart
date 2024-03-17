package it.tobaben.dart.game;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;

import java.util.List;

public interface DartLikeGame {
    /**
     * Adds a player to the game.
     *
     * @param player the player to add
     */
    void addPlayer(Player player);

    /**
     * Removes a player from the game.
     *
     * @param player the player to remove
     */
    void removePlayer(Player player);

    /**
     * Plays a turn with the given segment and multiplier for the current player.
     *
     * @param segment the segment to hit
     */
    void playTurn(Segment segment) throws InvalidStateException, GameOverException;

    /**
     * Returns the winner of the game.
     *
     * @return the winner of the game, or null if the game is not over
     */
    List<Player> getRanking();

    /**
     * Returns the current player.
     *
     * @return the current player
     */
    Player getCurrentPlayer();
}
