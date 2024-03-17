package it.tobaben.dart.game.default301;

import it.tobaben.dart.common.Player;
import it.tobaben.dart.game.DartLikeGame;
import it.tobaben.dart.game.board.DartSet;
import it.tobaben.dart.game.board.Segment;
import it.tobaben.dart.game.exceptions.BustException;
import it.tobaben.dart.game.exceptions.GameOverException;
import it.tobaben.dart.game.exceptions.InvalidStateException;
import it.tobaben.dart.game.exceptions.PlayerFinishedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class would represent the game itself, and would include information such as the number of players, the current player, and the current round.
 */
public class Game301 implements DartLikeGame {

    private final int THROW_LIMIT_PER_ROUND = 3;

    private boolean started;
    private final List<Player> players;
    private Player currentPlayer;
    private DartSet currentDartSet;
    private CountingScoreboard scoreboard;

    private final List<DartSet> dartSetHistory;

    public Game301(){
        this.dartSetHistory = new ArrayList<>();
        this.players = new ArrayList<>();
        this.started = false;
    }

    public Map<Player, Integer> getScoreboard(){
        return this.scoreboard.getScores();
    }

    public List<DartSet> getDartSetHistory(){
        return List.copyOf(this.dartSetHistory);
    }


    @Override
    public void addPlayer(Player player) {
        if(this.started){
            return;
        }
        this.players.add(player);
        if(this.currentPlayer == null){
            this.currentPlayer = player;
        }
    }

    @Override
    public void removePlayer(Player player) {
        if(this.started){
            return;
        }
        this.players.remove(player);
        if(this.players.isEmpty()){
            this.currentPlayer = null;
        }
    }

    @Override
    public void playTurn(Segment segment) throws InvalidStateException, GameOverException {
        if(this.players.isEmpty() || currentPlayer == null){
            throw new InvalidStateException("Game must be played by at least 1 Player OR currentPlayer is Null");
        }
        if(!this.started){
            this.scoreboard = new CountingScoreboard(Set.copyOf(this.players), 301, 0);
            this.started = true;
        }
        if(this.currentDartSet == null){
            this.currentDartSet = new DartSet(this.currentPlayer, THROW_LIMIT_PER_ROUND);
            this.scoreboard.startCount(this.currentPlayer);
        }
        currentDartSet.addThrow(segment);
        try {
            scoreboard.changeScore(segment.getScore());
        } catch (BustException e) {
            currentDartSet.busted();
            this.scoreboard.clearCommit();
        } catch (PlayerFinishedException e) {
            this.currentDartSet.finish();
            //this.scoreboard.clearCommit();
        }

        if(currentDartSet.isDone()){
           this.dartSetHistory.add(currentDartSet);
           this.scoreboard.commit();
           this.currentDartSet = null;
           this.currentPlayer = getNextPlayer();
        }
    }

    private Player getNextPlayer() throws InvalidStateException{
        int currentPlayerIndex = this.players.indexOf(currentPlayer);
        int nextPlayerIndex = (currentPlayerIndex + 1) % players.size();
        for(int i = 0; i < this.players.size(); i++){
            if(! this.scoreboard.getRanking().contains(this.players.get(nextPlayerIndex))){
                return this.players.get(nextPlayerIndex);
            } else {
                nextPlayerIndex = (nextPlayerIndex + 1) % players.size();
            }
        }
        throw new InvalidStateException("There is no next player!");
    }

    @Override
    public List<Player> getRanking() {
        return this.scoreboard.getRanking();
    }

    @Override
    public Player getCurrentPlayer() {
        return null;
    }

}
