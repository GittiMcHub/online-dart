import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Spieler {
    private int id;
    private int dartboardId;
    private String name;
    private int punktestand;
    private int freieWuerfe;
    private Statistik statistik;
    public Spieler(int id, String name, int dartboardId){
        this.setId(id);
        this.setName(name);
        this.statistik = new Statistik();
        this.setDartboardId(dartboardId);
    }

    public int wurfAuswerten(String wurfValue) throws IllegalArgumentException{
        Pattern p = Pattern.compile("[0-9][0-9][0-9]");
        Matcher m = p.matcher(wurfValue);

        if(wurfValue.length()!=3 || !m.find()){
            throw new IllegalArgumentException("[PLAYER] Falsche Nachrichtenlänge empfangen");
        }
        // Eingabe gültig, also ein Wurf frei weniger
        this.setFreieWuerfe(this.getFreieWuerfe()-1);

        //Reset
        if(wurfValue.equals("996")){
            return -1;
        }
        if(wurfValue.equals("997")){
            // Wandtreffer
            this.statistik.addWandTreffer();
            this.statistik.addStrafpunkt();
            this.statistik.calculateAvg(0);
        } else if (wurfValue.equals("998")) {
            // Randtreffer
            this.statistik.addRandTreffer();
            this.statistik.calculateAvg(0);
        } else if (wurfValue.equals("999")) {
            // Next
            this.setFreieWuerfe(0);
        }else { // Wurf ergab Punkte und die Punkte werden jetzt gezählt
            int multiplikator = Character.getNumericValue(wurfValue.charAt(0));
            int feldwert = (Character.getNumericValue(wurfValue.charAt(1)) * 10 + Character.getNumericValue(wurfValue.charAt(2)));
            int wert = feldwert * multiplikator;

            // ### Statistik 1ner Double Triple
            if(wurfValue.equals("125")){
                this.statistik.addBull();
            }else if (wurfValue.equals("225")) {
                this.statistik.addBullseye();
            }else {
                switch (multiplikator) {
                    case 1:
                        this.statistik.addEinerFeld();
                        break;
                    case 2:
                        this.statistik.addDoubleFeld();
                        break;
                    case 3:
                        this.statistik.addTripleFeld();
                        break;
                }
            }

            // ### Statistik Ende
            this.statistik.addGetroffenesFeld(multiplikator,feldwert);
            this.statistik.calculateAvg(wert);
            this.setPunktestand(this.getPunktestand() - wert);
        }

        if(this.getPunktestand() == 0){
            // Spieler hat gewonnen
            return 0;
        }else if(this.getPunktestand() < 0){
            // Spieler hat überworfen
            this.statistik.addUeberworfen();
            return -1;
        }else{
            // Spieler muss noch spielen
            return this.getPunktestand();
        }
    }

    // Weitere Methoden
    public int getWertVomWurf(String wurfValue){
        Pattern p = Pattern.compile("[0-9][0-9][0-9]");
        Matcher m = p.matcher(wurfValue);

        if(wurfValue.length()!=3 || !m.find()){
            return -1;
        }else{
            int multiplikator = Character.getNumericValue(wurfValue.charAt(0));
            return (Character.getNumericValue(wurfValue.charAt(1)) * 10 + Character.getNumericValue(wurfValue.charAt(2))) * multiplikator;
        }

    }

    public boolean punktestandIstSchnapszahl() {
        // Überprüfe, ob die Länge der Zahl mindestens 2 ist
        String punkteStand_str = Integer.toString(this.punktestand);
        if (punkteStand_str.length() < 2) {
            return false;
        }

        // Überprüfe, ob alle Ziffern gleich sind
        char ersteZiffer = punkteStand_str.charAt(0);
        for (int i = 1; i < punkteStand_str.length(); i++) {
            if (punkteStand_str.charAt(i) != ersteZiffer) {
                return false; // Es wurde eine unterschiedliche Ziffer gefunden
            }
        }

        return true; // Alle Ziffern sind gleich
    }

    // <--------------- GETTER und SETTER --------------->
    public int getDartboardId() {
        return dartboardId;
    }
    public Statistik getStatistics(){return this.statistik;}

    public void setDartboardId(int dartboardId) {
        this.dartboardId = dartboardId;
    }
    public int getId() {
        return id;
    }

    private void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    private void setName(String name) {
        this.name = name;
    }

    public int getPunktestand() {
        return punktestand;
    }

    public void setPunktestand(int punktestand) {
        this.punktestand = punktestand;
    }

    public int getFreieWuerfe() {
        return freieWuerfe;
    }

    public void setFreieWuerfe(int freieWuerfe) {
        this.freieWuerfe = freieWuerfe;
    }

    public void resetSpieldatenStatistik(){
        this.statistik.resetSpieldaten();
    }


}
