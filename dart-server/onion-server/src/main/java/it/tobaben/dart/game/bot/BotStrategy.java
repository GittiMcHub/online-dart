package it.tobaben.dart.game.bot;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.GameSnapshot;

/** Decides where a bot aims its next dart, from the game state alone. */
public interface BotStrategy {

    /** @return aim point for the next dart of self (self is the current player of the snapshot) */
    AimPoint aim(GameSnapshot snapshot, Player self);
}
