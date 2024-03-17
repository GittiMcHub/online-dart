package it.tobaben.dart.game.board;

import it.tobaben.dart.common.Player;

import java.util.ArrayList;
import java.util.List;

public class DartSet {
    private final int throwsLimit;
    private final Player player;
    private final List<Throw> dartThrows;

    private boolean busted = false;
    private boolean finished = false;

    public DartSet(Player player, int throwsLimit){
        this.player = player;
        this.throwsLimit = throwsLimit;
        this.dartThrows = new ArrayList<>();

    }
    public void addThrow(Segment segment){
        if(this.dartThrows.size() != throwsLimit && !this.busted){
            this.dartThrows.add(new Throw(player, segment));
        }
    }

    public void finish(){
        this.finished = true;
    }
    public void busted(){
        this.busted = true;
    }

    public boolean isDone(){
        return this.dartThrows.size() == throwsLimit || this.busted || this.finished;
    }
    public boolean isBusted(){
        return this.busted;
    }
    public boolean isFinish(){
        return this.finished;
    }

    public int getScore(){
        return this.busted ? 0 : this.dartThrows.stream().map(Throw::getScore).reduce(0, Integer::sum);
    }

    @Override
    public String toString(){
        return this.player.getName() + " // " + this.getScore() + (this.busted ? " > BUST <" : "");
    }
}
