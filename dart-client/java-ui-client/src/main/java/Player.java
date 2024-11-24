public class Player {
    private int id;
    private int dartboardId;
    private String name;
    private int punktestand;
    private int freieWuerfe;
    private Statistik statistik;

    // Getter und Setter
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDartboardId() {
        return dartboardId;
    }

    public void setDartboardId(int dartboardId) {
        this.dartboardId = dartboardId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
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

    public Statistik getStatistik() {
        return statistik;
    }

    public void setStatistik(Statistik statistik) {
        this.statistik = statistik;
    }
}
