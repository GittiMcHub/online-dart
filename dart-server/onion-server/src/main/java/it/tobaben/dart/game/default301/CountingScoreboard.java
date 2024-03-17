package it.tobaben.dart.game.default301;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.exceptions.BustException;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import it.tobaben.dart.game.exceptions.PlayerFinishedException;

import java.util.*;

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
    }

    public void startCount(Player player){
        this.currentPlayer = player;
        this.currentScore = scores.get(player);
    }

    public Map<Player, Integer> getScores() {
        return new HashMap<>(scores);
    }

    public void commit() throws GameOverException {
        if(currentPlayer == null || currentScore < 0){
            return;
        }
        scores.put(this.currentPlayer, this.currentScore);
        if(isGameOver()){
            // there must be one whos not finished when there are more than 1 player
            if(this.scores.size() > 1){
                this.ranking.add(this.scores.keySet().stream().filter(key -> this.scores.get(key) != targetScore).findFirst().get());
            }
            throw new GameOverException("Game is over: " + this.ranking.toString());
        }
        clearCommit();
    }

    public void clearCommit(){
        this.currentPlayer = null;
        this.currentScore = -1;
    }

    public List<Player> getRanking(){
        return new ArrayList<>(ranking);
    }

    public void changeScore(int score) throws BustException, InvalidStateException, PlayerFinishedException {
        if (mode == Mode.ADD_UP) {
            this.addScore(score);
        } else {
            this.subtractScore(score);
        }
        if(this.currentScore == this.targetScore ){
            ranking.add(this.currentPlayer);
            throw new PlayerFinishedException("Player " + currentPlayer.getName());
        }
    }

    private void subtractScore(int score) throws BustException, InvalidStateException {
        if(currentPlayer == null || currentScore < 0){
            throw new InvalidStateException("No Player set. Start a Commit first.");
        }
        this.currentScore = this.currentScore - score;
        if(this.currentScore < targetScore){
            throw new BustException(this.currentScore + " < " + targetScore);
        }
    }

    private void addScore(int score) throws BustException, InvalidStateException {
        if(currentPlayer == null || currentScore < 0){
            throw new InvalidStateException("No Player set. Start a Commit first.");
        }
        this.currentScore = this.currentScore - score;
        if(this.currentScore > targetScore){
            throw new BustException(this.currentScore + " < " + targetScore);
        }
        if(this.currentScore == targetScore){

        }
    }

    public int getScore(Player player){
        return this.scores.get(player);
    }

    private boolean isGameOver() {
        // TODO: hier ist ein bug! Bei mehr als zwei SPielern würde beim ersten 0er Score das spiel beenden!
        if(this.scores.size() <= 2){
            // Wenn nur ein spieler da ist, und der Score == 0 ist, dann ist das spiel vorbei, sonst nicht
            // Wenn einer von zwei spielern schon 0 hat, dann ist das spiel vorbei, sonst nicht
            return this.scores.values().stream().filter(s -> s == targetScore).count() == 1;
        }
        // Wenn es mehr als zwei Spieler gibt, dann ist das spiel erst vorbei, wenn nur noch einer nicht 0 erreicht hat
        return scores.values().stream().filter(s -> s > 0).count() <= 1;
    }


}
