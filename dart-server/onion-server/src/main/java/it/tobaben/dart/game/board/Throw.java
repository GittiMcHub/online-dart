package it.tobaben.dart.game.board;

import it.tobaben.dart.common.Player;

/**
 * This class would represent a single throw of a dart, and would include information such as the segment targeted,
 * the score achieved, and any other relevant details.
 */
public class Throw {
    private final Player player;
    private final Segment segment;
    private final int score;

    public Throw(Player player, Segment segment) {
        this.player = player;
        this.segment = segment;
        this.score = segment.getScore();
    }

    public int getScore() {
        return score;
    }
}
