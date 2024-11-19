public class Statistik {
    private int anzWuerfeSpiel;
    private int anzWuerfeTurnier;
    private int summeSpiel;
    private int summeTurnier;
    private int anzMinWuerfeBisSpielende;
    private int maxPunkteProSpielzug;
    private float avgSpiel;
    private float avgTurnier;
    private int anzStrafpunkte;
    private int anzRandTreffer;
    private int anzWandTreffer;
    private int anzBull;
    private int anzBullseye;
    private int anzEinerFeld;
    private int anzDoubleFeld;
    private int anzTripleFeld;
    private int anzUeberworfen;
    private int highestFinish;
    private int[][] getroffeneFelder;

    public Statistik() {
        this.anzWuerfeSpiel = 0;
        this.anzWuerfeTurnier = 0;
        this.summeSpiel = 0;
        this.summeTurnier = 0;
        this.anzMinWuerfeBisSpielende = 999;
        this.avgSpiel = 0.0f;
        this.avgTurnier = 0.0f;
        this.anzStrafpunkte = 0;
        this.anzRandTreffer = 0;
        this.anzWandTreffer = 0;
        this.anzBull = 0;
        this.anzBullseye = 0;
        this.anzEinerFeld = 0;
        this.anzDoubleFeld = 0;
        this.anzTripleFeld = 0;
        this.anzUeberworfen = 0;
        this.highestFinish = 0;
        // [3][21] 3, da [Single,Double und Triple Felder] und 21 für Zahlen [1 bis 20 & Bull]
        this.getroffeneFelder = new int[3][21];
    }

    public void calculateAvg(int wert) {
        this.summeSpiel += wert;
        this.summeTurnier += wert;

        this.avgSpiel = (this.summeSpiel / anzWuerfeSpiel) * 3;
        this.avgTurnier = (this.summeTurnier / anzWuerfeTurnier) * 3;
    }

    public void resetSpieldaten() {
        this.summeSpiel = 0;
        this.avgSpiel = 0.0f;
        this.anzWuerfeSpiel = 0;
    }

    public void printStatistics() {
        //System.out.println("[STATISTIK] Für Spieler: " + this.getSpieler().getName());
        System.out.println("[STATISTIK] Average Spiel: " + this.getAvgSpiel());
        System.out.println("[STATISTIK] Average Turnier " + this.getAvgTurnier());
        System.out.println("[STATISTIK] Max Punkte bei 3 Würfen: " + this.getMaxPunkteProSpielzug());
        System.out.println("[STATISTIK] Min Anzahl Würfe bis Sieg: " + this.getAnzMinWuerfeBisSpielende());
        System.out.println("[STATISTIK] Anzahl Würfe Spiel: " + this.getAnzWuerfeSpiel());
        System.out.println("[STATISTIK] Anzahl Würfe Turnier: " + this.getAnzWuerfeTurnier());
        System.out.println("[STATISTIK] Anzahl 1x Feld: " + this.getAnzEinerFeld());
        System.out.println("[STATISTIK] Anzahl 2x Feld: " + this.getAnzDoubleFeld());
        System.out.println("[STATISTIK] Anzahl 3x Feld: " + this.getAnzTripleFeld());
        System.out.println("[STATISTIK] Anzahl Bull: " + this.getAnzBull());
        System.out.println("[STATISTIK] Anzahl Bullseye: " + this.getAnzBullseye());
        System.out.println("[STATISTIK] Anzahl Randtreffer: " + this.getAnzRandTreffer());
        System.out.println("[STATISTIK] Anzahl Wandtreffer: " + this.getAnzWandTreffer());
        System.out.println("[STATISTIK] Strafpunkte: " + this.getAnzStrafpunkte());
    }

    // <--------------- ADD Methoden --------------->
    public void addStrafpunkt() {
        this.anzStrafpunkte++;
    }

    public void addStrafpunkte(int anzahl) {
        this.anzStrafpunkte = this.anzStrafpunkte + anzahl;
    }

    public void addRandTreffer() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzRandTreffer++;
    }

    public void addWandTreffer() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzWandTreffer++;
    }

    public void addBull() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzBull++;
    }

    public void addBullseye() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzBullseye++;
    }

    public void addEinerFeld() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzEinerFeld++;
    }

    public void addDoubleFeld() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzDoubleFeld++;
    }

    public void addTripleFeld() {
        this.anzWuerfeSpiel++;
        this.anzWuerfeTurnier++;
        this.anzTripleFeld++;
    }

    public void addUeberworfen() {
        this.anzUeberworfen++;
    }

    // <--------------- GETTER und SETTER --------------->
    //public Spieler getSpieler() {return spieler;}
    public int getAnzWuerfeSpiel() {
        return anzWuerfeSpiel;
    }

    public int getAnzWuerfeTurnier() {
        return anzWuerfeTurnier;
    }

    public int getAnzMinWuerfeBisSpielende() {
        return anzMinWuerfeBisSpielende;
    }

    public void setAnzMinWuerfeBisSpielende(int anzMinWuerfeBisSpielende) {
        if (anzMinWuerfeBisSpielende < this.anzMinWuerfeBisSpielende) {
            this.anzMinWuerfeBisSpielende = anzMinWuerfeBisSpielende;
        }
    }

    public void setMaxPunkteProSpielzug(int maxPunkteProSpielzug) {
        if (maxPunkteProSpielzug > this.maxPunkteProSpielzug) {
            this.maxPunkteProSpielzug = maxPunkteProSpielzug;
        }
    }

    public void setHighestFinish(int finishedPoints) {
        if (this.highestFinish < finishedPoints) {
            this.highestFinish = finishedPoints;
        }
    }

    // Diese Funktion wird nur aufgerufen, wenn es einen Wurf gab, der Punkte zählte.
    // D.h. 999 für Next o.ä. muss hier nicht abgefragt werden
    public void addGetroffenesFeld(int multiplikator, int wert) {
        //Bull Wert hat 25, wollen aber in 21. Stelle schreiben bzw. Index 20
        if (wert > 20) {
            // Multiplikator ist bei Bull nur maximal 2
            getroffeneFelder[multiplikator-1][20]++;
        } else {
            getroffeneFelder[multiplikator-1][wert - 1]++;
        }
    }

    public float getAvgSpiel() {
        return avgSpiel;
    }

    public float getAvgTurnier() {
        return avgTurnier;
    }

    public int getAnzStrafpunkte() {
        return anzStrafpunkte;
    }

    public int getAnzRandTreffer() {
        return anzRandTreffer;
    }

    public int getAnzWandTreffer() {
        return anzWandTreffer;
    }

    public int getAnzBull() {
        return anzBull;
    }

    public int getAnzBullseye() {
        return anzBullseye;
    }

    public int getAnzEinerFeld() {
        return anzEinerFeld;
    }

    public int getAnzDoubleFeld() {
        return anzDoubleFeld;
    }

    public int getAnzTripleFeld() {
        return anzTripleFeld;
    }

    public int getMaxPunkteProSpielzug() {
        return maxPunkteProSpielzug;
    }

}
