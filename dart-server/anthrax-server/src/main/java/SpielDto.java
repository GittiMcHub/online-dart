import java.util.ArrayList;

public class SpielDto {
    public ArrayList<Spieler> spielerPlatzierung;
    public ArrayList<Spieler> spielerReihenfolge;
    public Spieler currentPlayer;
    public int kostenStrafpunkte;
    public int anzahlSpiele;
    public int spielId;
    public int punkteSpielzug;
    public int letzterWurf;
    public GameState gameState;
}
