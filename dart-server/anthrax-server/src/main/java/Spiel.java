import java.util.ArrayList;

public class Spiel implements Runnable {
    private int id;
    private ArrayList<Spieler> spielerListe;
    private ArrayList<Spieler> spielerPlatzierung;
    private ArrayList<Spieler> spielerReihenfolge;
    private Spieler currentPlayer;
    private SharedData sharedData;
    private MqttHandler mqttHandler;
    private int anzahlSpieleTurnier;
    private int kostenStrafpunkt;
    private int letzerWurf;
    private int punkteSpielzug;
    private GameState gameState;
    public Spiel(int id, ArrayList<Spieler> spielerListe, SharedData sharedData, MqttHandler mqttHandler) {
        this.setId(id);
        this.setSpielerListe(spielerListe);
        this.currentPlayer = spielerListe.get(0);
        spielerPlatzierung = new ArrayList<>();
        this.sharedData = sharedData;
        this.mqttHandler = mqttHandler;
        this.anzahlSpieleTurnier = 0;
        this.kostenStrafpunkt = 0;
        this.gameState = GameState.UNDEFINED;
    }

    public Spiel(int id, ArrayList<Spieler> spielerListe, SharedData sharedData, MqttHandler mqttHandler, int anzahlSpieleTurnier, int kostenStrafpunkt) {
        this.setId(id);
        this.setSpielerListe(spielerListe);
        spielerPlatzierung = new ArrayList<>();
        this.sharedData = sharedData;
        this.mqttHandler = mqttHandler;
        this.anzahlSpieleTurnier = anzahlSpieleTurnier;
        this.kostenStrafpunkt = kostenStrafpunkt;
        this.currentPlayer = spielerListe.get(0);
    }
    @Override
    public void run() {
        //<---------------------- Spiel START ---------------------->
        this.mqttHandler.publishSound(Sounds.SPIELSTART);
        this.gameState = GameState.RUNNING;
        GameDataPublisher gameDataPublisher = new GameDataPublisher(this);
        gameDataPublisher.publishGameData();
        boolean gameRunning = true;
        while (gameRunning) {
            if (spielerPlatzierung.size() >= spielerListe.size()) {
                // Alle Spieler wurden einer Platzierung zugewiesen
                System.out.println("[Spiel] Alle Spieler bereits fertig! (1)");
                gameRunning = false;
                break;
            }
            // Spieler Spielzug
            for (Spieler spielzugAktuellerSpieler : spielerReihenfolge) {
                if (spielerPlatzierung.size() >= spielerListe.size()) {
                    System.out.println("[Spiel] Alle Spieler bereits fertig! (2)");
                    // Alle Spieler wurden einer Platzierung zugewiesen
                    gameRunning = false;
                    break;
                }

                // Prüfen ob Spieler bereits Spielzug beendet hat
                if (!(spielerPlatzierung.contains(spielzugAktuellerSpieler))) {
                    //<---------------------- Spielzug START ---------------------->
                    currentPlayer = spielzugAktuellerSpieler;
                    int punkteStandAnfangSpielzug = spielzugAktuellerSpieler.getPunktestand();
                    this.punkteSpielzug = 0;
                    this.letzerWurf = 0;
                    spielzugAktuellerSpieler.setFreieWuerfe(3);
                    while (spielzugAktuellerSpieler.getFreieWuerfe() > 0) {
                        gameDataPublisher.publishGameData();
                        System.out.println("[Spiel] Aktueller Spieler: " + spielzugAktuellerSpieler.getName());
                        System.out.println("[Spiel] Freie Würfe: " + spielzugAktuellerSpieler.getFreieWuerfe());
                        System.out.println("[Spiel] Punktestand: " + spielzugAktuellerSpieler.getPunktestand());
                        System.out.println("[Spiel] Warte auf Eingabe...");
                        long lastMessageId = sharedData.getLastMessageId();
                        String message = "";
                        try {
                            message = sharedData.waitForMessage(lastMessageId, spielzugAktuellerSpieler.getDartboardId());
                        }catch (Exception ex){
                            ex.printStackTrace();
                        }

                        switch (message){
                            // NEXT
                            case "999":
                                System.out.println("[Spiel] NEXT");
                                this.mqttHandler.publishSound(Sounds.TREFFER);
                                // Spieler darf dann nicht mehr Werfen
                                spielzugAktuellerSpieler.setFreieWuerfe(0);
                                break;
                            // Randtreffer bzw. Kein Treffer
                            case "998":
                                System.out.println("[Spiel] Kein Treffer!");
                                spielzugAktuellerSpieler.wurfAuswerten("998");
                                this.mqttHandler.publishSound(Sounds.RESET);
                                this.letzerWurf = 0;
                                break;
                            // Wandtreffer
                            case "997":
                                System.out.println("[Spiel] § Wandtreffer !");
                                this.mqttHandler.publishSound(Sounds.STRAFE);
                                spielzugAktuellerSpieler.wurfAuswerten("997");
                                this.letzerWurf = 0;
                                break;
                            // Bounced out
                            case "996":
                                System.out.println("[Spiel] Bounced out");
                                this.mqttHandler.publishSound(Sounds.RESET);
                                spielzugAktuellerSpieler.setFreieWuerfe(0);
                                // Wert auf anfang zurücksetzen
                                spielzugAktuellerSpieler.setPunktestand(punkteStandAnfangSpielzug);
                                this.letzerWurf = 0;
                                break;
                            default:
                                // ################ Regulärer Wurf ################
                                System.out.println("[Spiel] Treffer!");
                                int punkteStandNachWurf = spielzugAktuellerSpieler.wurfAuswerten(message);
                                // Für Statistik
                                this.punkteSpielzug += spielzugAktuellerSpieler.getWertVomWurf(message);
                                this.letzerWurf = spielzugAktuellerSpieler.getWertVomWurf(message);

                                switch (punkteStandNachWurf) {
                                    // ################ GEWONNEN ################
                                    case 0:
                                        //Spieler hat gewonnen
                                        this.mqttHandler.publishSound(Sounds.WINNER);
                                        spielerPlatzierung.add(spielzugAktuellerSpieler);
                                        System.out.println("[Spiel] Spieler: " + spielzugAktuellerSpieler.getName() + " hat sein Spiel beendet!");
                                        spielzugAktuellerSpieler.setFreieWuerfe(0);
                                        // Setze Würfe bis Spielende
                                        int wuerfeSpiel = spielzugAktuellerSpieler.getStatistics().getAnzWuerfeSpiel();
                                        spielzugAktuellerSpieler.getStatistics().setAnzMinWuerfeBisSpielende(wuerfeSpiel);
                                        // Setze highest finish
                                        spielzugAktuellerSpieler.getStatistics().setHighestFinish(this.punkteSpielzug);
                                        // Wenn er vorletzter wurde den letzen hinzufügen
                                        if (spielerPlatzierung.size() == (spielerListe.size() - 1)) {
                                            System.out.println("[Spiel] Alle bis auf der Letzte haben das Spiel beendet");
                                            for (Spieler letzterSpieler : spielerListe) {
                                                if (!spielerPlatzierung.contains(letzterSpieler)) {
                                                    spielerPlatzierung.add(letzterSpieler);
                                                }
                                            }
                                            gameRunning = false;
                                        }
                                        for (int i = 0; i < spielerPlatzierung.size(); i++) {
                                            System.out.println("[Spiel] Platz " + (i + 1) + ": " + spielerPlatzierung.get(i).getName());
                                        }
                                        break;
                                    // ################ ÜBERWORFEN ################
                                    case -1:
                                        // Spieler hat überworfen
                                        this.mqttHandler.publishSound(Sounds.UEBERWORFEN);
                                        System.out.println("[Spiel] Spieler hat überworfen! Rücksetzen auf Punktestand: " + punkteStandAnfangSpielzug);
                                        spielzugAktuellerSpieler.setPunktestand(punkteStandAnfangSpielzug);
                                        // Darf nicht weiter werfen
                                        spielzugAktuellerSpieler.setFreieWuerfe(0);
                                        break;
                                    default:
                                        // ################ Punktestand noch > 0 ################
                                        if(message.equals("225")){
                                            this.mqttHandler.publishSound(Sounds.BULLSEYE);
                                        } else if (message.charAt(0) == '3') {
                                            this.mqttHandler.publishSound(Sounds.TRIPLE);
                                        } else if (message.charAt(0) == '2') {
                                            this.mqttHandler.publishSound(Sounds.DOUBLE);
                                        }else {
                                            this.mqttHandler.publishSound(Sounds.TREFFER);
                                        }
                                        System.out.println("[Spiel] Wurf ausgewertet");
                                }
                        }
                        gameDataPublisher.publishGameData();
                    }//<---------------------- Spielzug ENDE ---------------------->
                    // Höchste Punkte bei 3 Würfen setzen
                    spielzugAktuellerSpieler.getStatistics().setMaxPunkteProSpielzug(punkteSpielzug);
                    // Beim Überwerfen oder Bounce Out nicht erneut Strafpunkte bei Schnapszahl vergeben
                    if((punkteStandAnfangSpielzug != spielzugAktuellerSpieler.getPunktestand()) && spielzugAktuellerSpieler.punktestandIstSchnapszahl()){
                        this.mqttHandler.publishSound(Sounds.STRAFE);
                        spielzugAktuellerSpieler.getStatistics().addStrafpunkt();
                    }
                    if(this.punkteSpielzug == 180){
                        this.mqttHandler.publishSound(Sounds.MAXPOINTS);
                    }
                    // ################ warten auf NEXT oder RESET ################
                    System.out.println("[Spiel] Warte auf NEXT Button...");
                    long lastMessageId = sharedData.getLastMessageId();
                    String message = "";
                    this.gameState = GameState.WAITING;
                    gameDataPublisher.publishGameData();
                    boolean waitingForNext = true;
                    while (waitingForNext){
                        try {
                            message = sharedData.waitForMessage(lastMessageId);
                        }catch (Exception ex){
                            ex.printStackTrace();
                        }
                        if(message.equals("999")){
                            System.out.println("[Spiel] Next empfangen");
                            this.mqttHandler.publishSound(Sounds.TREFFER);
                            waitingForNext = false;
                        }
                        if(message.equals("996")){
                            // Punkte zurücksetzen. Da Falsch geworfen
                            System.out.println("[Spiel] Bounced out! (1)");
                            this.mqttHandler.publishSound(Sounds.RESET);
                            spielzugAktuellerSpieler.setPunktestand(punkteStandAnfangSpielzug);
                            lastMessageId = sharedData.getLastMessageId();
                        }
                    }
                    this.gameState = GameState.RUNNING;
                    gameDataPublisher.publishGameData();
                }
            }//<---------------------- nächster Spieler ---------------------->
        }//<---------------------- Spiel ENDE ---------------------->
        for(int i = 0 ; i< spielerPlatzierung.size();i++){
            spielerPlatzierung.get(i).getStatistics().addStrafpunkte(i+1);
        }
        // ################ warten auf NEXT ################
        System.out.println("[Spiel ENDE] Warte auf NEXT Button...");
        long lastMessageId = sharedData.getLastMessageId();
        String message = "";
        this.gameState = GameState.FINISHED;
        gameDataPublisher.publishGameData();
        boolean waitingForNext = true;
        while (waitingForNext){
            try {
                message = sharedData.waitForMessage(lastMessageId);
            }catch (Exception ex){
                ex.printStackTrace();
            }
            if(message.equals("999")){
                System.out.println("[Spiel ENDE] Next empfangen");
                this.mqttHandler.publishSound(Sounds.TREFFER);
                waitingForNext = false;
            }
        }
        for (Spieler spieler : spielerListe) {
            spieler.getStatistics().printStatistics();
        }
    }


    // <--------------- GETTER und SETTER --------------->
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public ArrayList<Spieler> getSpielerListe() {
        return spielerListe;
    }

    public void setSpielerListe(ArrayList<Spieler> spielerListe) {
        this.spielerListe = spielerListe;
    }

    public ArrayList<Spieler> getSpielerPlatzierung() {
        return spielerPlatzierung;
    }

    public void setSpielerPlatzierung(ArrayList<Spieler> spielerPlatzierung) {
        this.spielerPlatzierung = spielerPlatzierung;
    }

    public ArrayList<Spieler> getSpielerReihenfolge() {
        return spielerReihenfolge;
    }

    public void setSpielerReihenfolge(ArrayList<Spieler> spielerReihenfolge) {
        this.spielerReihenfolge = spielerReihenfolge;
    }

    public Spieler getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(Spieler currentPlayer) {
        this.currentPlayer = currentPlayer;
    }

    public MqttHandler getMqttHandler() {
        return mqttHandler;
    }

    public int getAnzahlSpieleTurnier() {
        return anzahlSpieleTurnier;
    }

    public int getKostenStrafpunkt() {
        return kostenStrafpunkt;
    }

    public int getLetzerWurf() {
        return letzerWurf;
    }

    public int getPunkteSpielzug() {
        return punkteSpielzug;
    }

    public GameState getGameState() {
        return gameState;
    }
}
