import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;

public class GameData {
    private static final GameData instance = new GameData();
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private List<Player> spielerPlatzierung;
    private List<Player> spielerReihenfolge;
    private Player currentPlayer;
    private int kostenStrafpunkte;
    private int anzahlSpiele;
    private int spielId;
    private int punkteSpielzug;
    private int letzterWurf;
    private String gameState;

    public GameData() {
        this.spielerPlatzierung = new ArrayList<>();
        this.spielerReihenfolge = new ArrayList<>();
    }

    public static GameData getInstance() {
        return instance;
    }

    public void updateFromMqttMessage(String jsonMessage) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            GameData data = objectMapper.readValue(jsonMessage, GameData.class);

            this.spielerPlatzierung = data.spielerPlatzierung;
            this.spielerReihenfolge = data.spielerReihenfolge;
            this.currentPlayer = data.currentPlayer;
            this.kostenStrafpunkte = data.kostenStrafpunkte;
            this.anzahlSpiele = data.anzahlSpiele;
            this.spielId = data.spielId;
            this.punkteSpielzug = data.punkteSpielzug;
            this.letzterWurf = data.letzterWurf;
            this.gameState = data.gameState;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Fehler beim Einlesen der Nachricht: " + e.getMessage());
        }
        notifyChange();
    }

    // Getter und Setter für die Felder
    public List<Player> getSpielerReihenfolge() {
        return spielerReihenfolge;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public int getKostenStrafpunkte() {
        return kostenStrafpunkte;
    }

    public int getAnzahlSpiele() {
        return anzahlSpiele;
    }

    public int getSpielId() {
        return spielId;
    }

    public int getPunkteSpielzug() {
        return punkteSpielzug;
    }

    public int getLetzterWurf() {
        return letzterWurf;
    }

    public String getGameState() {
        return gameState;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    // Methode, um Änderungen an GameData zu signalisieren
    public void notifyChange() {
        support.firePropertyChange("gamedata", null, this);
    }
}
