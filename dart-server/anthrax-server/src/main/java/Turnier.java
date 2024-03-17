import java.util.ArrayList;

public class Turnier {
    private ArrayList<Spiel> spiele;

    public Turnier(ArrayList<Spiel> spiele){
        this.setSpiele(spiele);
    }



    // <--------------- GETTER und SETTER --------------->
    public ArrayList<Spiel> getSpiele() {
        return spiele;
    }

    public void setSpiele(ArrayList<Spiel> spiele) {
        this.spiele = spiele;
    }
}
