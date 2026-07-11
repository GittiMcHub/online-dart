package it.tobaben.dart.game.x01;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.exceptions.BustException;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import it.tobaben.dart.game.exceptions.PlayerFinishedException;

import java.util.*;

/**
 * Transaction-style scoreboard for counting games: startCount(player) opens a turn,
 * changeScore() applies throws, commit() persists the turn or clearCommit() voids it (bust).
 * Counts down (SUBTRACT, e.g. 301 -> 0) or up (ADD_UP) depending on start and target score.
 */
public class CountingScoreboard {

    public enum Mode {
        ADD_UP, SUBTRACT
    }

    private final Mode mode;
    private final int targetScore;
    private final Map<Player, Integer> scores;
    private final List<Player> ranking;
    private Player currentPlayer;
    private int currentScore;

    public CountingScoreboard(Set<Player> players, int startingScore, int targetScore) {
        if(startingScore > targetScore){
            this.mode = Mode.SUBTRACT;
        } else {
            this.mode = Mode.ADD_UP;
        }

        this.scores = new HashMap<>();
        this.ranking = new ArrayList<>();
        for (Player player : players) {
            scores.put(player, startingScore);
        }
        this.targetScore = targetScore;
        clearCommit();
    }

    public void startCount(Player player){
        this.currentPlayer = player;
        this.currentScore = scores.get(player);
    }

    public Map<Player, Integer> getScores() {
        return new HashMap<>(scores);
    }

    public void commit() throws GameOverException {
        if(currentPlayer == null){
            return;
        }
        scores.put(this.currentPlayer, this.currentScore);
        clearCommit();
        if(isGameOver()){
            // complete the ranking: whoever has not finished gets the last place
            this.scores.keySet().stream()
                    .filter(player -> !this.ranking.contains(player))
                    .forEach(this.ranking::add);
            throw new GameOverException("Game is over: " + this.ranking);
        }
    }

    public void clearCommit(){
        this.currentPlayer = null;
        this.currentScore = Integer.MIN_VALUE;
    }

    public List<Player> getRanking(){
        return new ArrayList<>(ranking);
    }

    public void changeScore(int score) throws BustException, InvalidStateException, PlayerFinishedException {
        changeScore(score, true);
    }

    /**
     * Applies a throw to the currently open count.
     *
     * @param score the (effective) score of the throw
     * @param finishingThrow whether this throw is allowed to finish the game
     *                       (false e.g. for a non-double throw when double-out is required)
     */
    public void changeScore(int score, boolean finishingThrow) throws BustException, InvalidStateException, PlayerFinishedException {
        if(currentPlayer == null){
            throw new InvalidStateException("No Player set. Start a Commit first.");
        }
        if (mode == Mode.ADD_UP) {
            this.addScore(score);
        } else {
            this.subtractScore(score);
        }
        if(this.currentScore == this.targetScore){
            if(!finishingThrow){
                throw new BustException("Target score reached but throw may not finish the game");
            }
            ranking.add(this.currentPlayer);
            throw new PlayerFinishedException("Player " + currentPlayer.getName());
        }
    }

    private void subtractScore(int score) throws BustException {
        this.currentScore = this.currentScore - score;
        if(this.currentScore < targetScore){
            throw new BustException(this.currentScore + " < " + targetScore);
        }
    }

    private void addScore(int score) throws BustException {
        this.currentScore = this.currentScore + score;
        if(this.currentScore > targetScore){
            throw new BustException(this.currentScore + " > " + targetScore);
        }
    }

    public int getScore(Player player){
        return this.scores.get(player);
    }

    /**
     * Overrides a player's committed score (used to undo a turn). Does not touch
     * an open count.
     */
    public void overrideScore(Player player, int score){
        this.scores.put(player, score);
    }

    public void removeFromRanking(Player player){
        this.ranking.remove(player);
    }

    /**
     * @return the staged score of the currently open count
     */
    public int getCurrentScore() throws InvalidStateException {
        if(currentPlayer == null){
            throw new InvalidStateException("No Player set. Start a Commit first.");
        }
        return this.currentScore;
    }

    private boolean isGameOver() {
        long notFinished = this.scores.values().stream().filter(s -> s != targetScore).count();
        if(this.scores.size() == 1){
            // a single player plays until he reaches the target score
            return notFinished == 0;
        }
        // multiple players play until only one has not reached the target score
        return notFinished <= 1;
    }

}
