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

    /**
     * Returns whether the game is over. Once true, playTurn must not be called anymore.
     *
     * @return true if the game is over and the ranking is final
     */
    boolean isOver();

    /**
     * Returns the events emitted by the most recent playTurn call. Also valid when
     * playTurn ended with a GameOverException (the buffer then contains GAME_OVER).
     *
     * @return the events of the last played turn, oldest first
     */
    List<GameEvent> getLastTurnEvents();

    /**
     * Returns a mode-agnostic view of the current game state for publishing.
     *
     * @return the current game state snapshot
     */
    GameSnapshot getSnapshot();

    /**
     * Ends the current turn early (NEXT pressed): darts already thrown in the open
     * turn keep their effect, remaining darts are forfeited, play passes on.
     */
    void endTurn() throws InvalidStateException, GameOverException;

    /**
     * Aborts the current turn (bounce out / wrong throw): all effects of the open
     * turn are voided and play passes to the next player.
     */
    void abortTurn() throws InvalidStateException;

    /**
     * Reverts the effects of the last completed turn (bounce out reported after the
     * turn ended). Idempotent; play order is not changed. No-op if no turn was
     * played yet.
     */
    void undoLastTurn();
}
