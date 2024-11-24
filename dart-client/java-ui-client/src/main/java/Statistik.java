import java.util.List;

public class Statistik {
    private int anzWuerfeSpiel;
    private int anzWuerfeTurnier;
    private int summeSpiel;
    private int summeTurnier;
    private int anzMinWuerfeBisSpielende;
    private int maxPunkteProSpielzug;
    private double avgSpiel;
    private double avgTurnier;
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
    private List<List<Integer>> getroffeneFelder;

    // Standardkonstruktor
    public Statistik() {
    }

    // Getter und Setter
    public int getAnzWuerfeSpiel() {
        return anzWuerfeSpiel;
    }

    public void setAnzWuerfeSpiel(int anzWuerfeSpiel) {
        this.anzWuerfeSpiel = anzWuerfeSpiel;
    }

    public int getAnzWuerfeTurnier() {
        return anzWuerfeTurnier;
    }

    public void setAnzWuerfeTurnier(int anzWuerfeTurnier) {
        this.anzWuerfeTurnier = anzWuerfeTurnier;
    }

    public int getSummeSpiel() {
        return summeSpiel;
    }

    public void setSummeSpiel(int summeSpiel) {
        this.summeSpiel = summeSpiel;
    }

    public int getSummeTurnier() {
        return summeTurnier;
    }

    public void setSummeTurnier(int summeTurnier) {
        this.summeTurnier = summeTurnier;
    }

    public int getAnzMinWuerfeBisSpielende() {
        return anzMinWuerfeBisSpielende;
    }

    public void setAnzMinWuerfeBisSpielende(int anzMinWuerfeBisSpielende) {
        this.anzMinWuerfeBisSpielende = anzMinWuerfeBisSpielende;
    }

    public int getMaxPunkteProSpielzug() {
        return maxPunkteProSpielzug;
    }

    public void setMaxPunkteProSpielzug(int maxPunkteProSpielzug) {
        this.maxPunkteProSpielzug = maxPunkteProSpielzug;
    }

    public double getAvgSpiel() {
        return avgSpiel;
    }

    public void setAvgSpiel(double avgSpiel) {
        this.avgSpiel = avgSpiel;
    }

    public double getAvgTurnier() {
        return avgTurnier;
    }

    public void setAvgTurnier(double avgTurnier) {
        this.avgTurnier = avgTurnier;
    }

    public int getAnzStrafpunkte() {
        return anzStrafpunkte;
    }

    public void setAnzStrafpunkte(int anzStrafpunkte) {
        this.anzStrafpunkte = anzStrafpunkte;
    }

    public int getAnzRandTreffer() {
        return anzRandTreffer;
    }

    public void setAnzRandTreffer(int anzRandTreffer) {
        this.anzRandTreffer = anzRandTreffer;
    }

    public int getAnzWandTreffer() {
        return anzWandTreffer;
    }

    public void setAnzWandTreffer(int anzWandTreffer) {
        this.anzWandTreffer = anzWandTreffer;
    }

    public int getAnzBull() {
        return anzBull;
    }

    public void setAnzBull(int anzBull) {
        this.anzBull = anzBull;
    }

    public int getAnzBullseye() {
        return anzBullseye;
    }

    public void setAnzBullseye(int anzBullseye) {
        this.anzBullseye = anzBullseye;
    }

    public int getAnzEinerFeld() {
        return anzEinerFeld;
    }

    public void setAnzEinerFeld(int anzEinerFeld) {
        this.anzEinerFeld = anzEinerFeld;
    }

    public int getAnzDoubleFeld() {
        return anzDoubleFeld;
    }

    public void setAnzDoubleFeld(int anzDoubleFeld) {
        this.anzDoubleFeld = anzDoubleFeld;
    }

    public int getAnzTripleFeld() {
        return anzTripleFeld;
    }

    public void setAnzTripleFeld(int anzTripleFeld) {
        this.anzTripleFeld = anzTripleFeld;
    }

    public int getAnzUeberworfen() {
        return anzUeberworfen;
    }

    public void setAnzUeberworfen(int anzUeberworfen) {
        this.anzUeberworfen = anzUeberworfen;
    }

    public int getHighestFinish() {
        return highestFinish;
    }

    public void setHighestFinish(int highestFinish) {
        this.highestFinish = highestFinish;
    }

    public List<List<Integer>> getGetroffeneFelder() {
        return getroffeneFelder;
    }

    public void setGetroffeneFelder(List<List<Integer>> getroffeneFelder) {
        this.getroffeneFelder = getroffeneFelder;
    }

    @Override
    public String toString() {
        return "Statistik{" +
                "anzWuerfeSpiel=" + anzWuerfeSpiel +
                ", anzWuerfeTurnier=" + anzWuerfeTurnier +
                ", summeSpiel=" + summeSpiel +
                ", summeTurnier=" + summeTurnier +
                ", anzMinWuerfeBisSpielende=" + anzMinWuerfeBisSpielende +
                ", maxPunkteProSpielzug=" + maxPunkteProSpielzug +
                ", avgSpiel=" + avgSpiel +
                ", avgTurnier=" + avgTurnier +
                ", anzStrafpunkte=" + anzStrafpunkte +
                ", anzRandTreffer=" + anzRandTreffer +
                ", anzWandTreffer=" + anzWandTreffer +
                ", anzBull=" + anzBull +
                ", anzBullseye=" + anzBullseye +
                ", anzEinerFeld=" + anzEinerFeld +
                ", anzDoubleFeld=" + anzDoubleFeld +
                ", anzTripleFeld=" + anzTripleFeld +
                ", anzUeberworfen=" + anzUeberworfen +
                ", highestFinish=" + highestFinish +
                ", getroffeneFelder=" + getroffeneFelder +
                '}';
    }
}
