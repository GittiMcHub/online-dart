import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class DistributionPanel extends JPanel implements PropertyChangeListener {
    private int[][] getroffeneFelder; // [3][21]: Single, Double, Triple
    private String playerName;
    private int playerPunktestand;
    private double playerAvgSpiel;
    private double playerAvgTurnier;


    public DistributionPanel() {
        GameData.getInstance().addPropertyChangeListener(this);
        this.getroffeneFelder = new int[3][21];
        this.playerName = "wartend..";
        this.playerPunktestand = 0;
        this.playerAvgSpiel = 0;
        this.playerAvgTurnier = 0;
        setPreferredSize(new Dimension(600, 600)); // Standardgröße für die Grafik
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Anti-Aliasing aktivieren
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = Math.min(width, height) / 2 - 40;

        // Berechne den maximalen Trefferwert für die Farbschattierung
        int maxTreffer = getMaxTreffer();

        // Zeichne die Ringe der Dartscheibe (Double, Single, Triple)
        drawDartboard(g2d, centerX, centerY, radius, getroffeneFelder, maxTreffer);

        // Hole den aktuellen Spieler und zeichne den Namen und die Punktzahl
        if(GameData.getInstance().getCurrentPlayer() != null){
            this.playerName = GameData.getInstance().getCurrentPlayer().getName();
            this.playerPunktestand = GameData.getInstance().getCurrentPlayer().getPunktestand();  // Hole die Punktzahl
            this.playerAvgSpiel = GameData.getInstance().getCurrentPlayer().getStatistik().getAvgSpiel();
            this.playerAvgTurnier = GameData.getInstance().getCurrentPlayer().getStatistik().getAvgTurnier();
        }

        // Setze die Schriftart und die Farbe für den Text
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 18));
        // Zeichne den Namen des Spielers unten im Bild
        String nameText = "Player: " + this.playerName;
        g2d.drawString(nameText, 20, height - 50);

        // Zeichne die Punktzahl des Spielers
        String scoreText = "Score: " + this.playerPunktestand;
        g2d.drawString(scoreText, 20, height - 20);

        // AVG Spiel / Turnier
        String AvgSpielText = "    Avg Spiel: " + this.playerAvgSpiel;
        g2d.drawString(AvgSpielText, width - 160, height - 50);
        String AvgTurnierText = "Avg Turnier: " + this.playerAvgTurnier;
        g2d.drawString(AvgTurnierText, width - 160, height - 20);

    }

    private int getMaxTreffer() {
        int max = 0;
        for (int ring = 0; ring < getroffeneFelder.length; ring++) {
            for (int feld = 0; feld < getroffeneFelder[ring].length; feld++) {
                max = Math.max(max, getroffeneFelder[ring][feld]);
            }
        }
        return max;
    }

    private Color getColorForTreffer(int treffer, int maxTreffer) {
        if (treffer == 0) {
            return Color.WHITE; // Kein Treffer -> Weiß
        }

        // Start- und Endfarbe definieren (z. B. Hellblau -> Dunkelblau)
        Color startColor = new Color(173, 216, 230); // Hellblau
        Color endColor = new Color(0, 0, 139);      // Dunkelblau

        // Interpolationsfaktor berechnen (zwischen 0 und 1)
        double factor = Math.min(1.0, treffer / (double) maxTreffer);

        // Farben interpolieren
        int red = (int) (startColor.getRed() * (1 - factor) + endColor.getRed() * factor);
        int green = (int) (startColor.getGreen() * (1 - factor) + endColor.getGreen() * factor);
        int blue = (int) (startColor.getBlue() * (1 - factor) + endColor.getBlue() * factor);

        return new Color(red, green, blue);
    }


    private void drawDartboard(Graphics2D g2d, int centerX, int centerY, int radius, int[][] hitDistribution, int maxHits) {
        // Radien der Ringe
        double outerRadius = radius; // Äußerer Radius der Dartscheibe
        double doubleRingWidth = 0.05 * radius; // Double-Feld Breite
        double singleOuterWidth = 0.25 * radius; // Äußeres Single-Feld
        double tripleRingWidth = 0.05 * radius; // Triple-Feld Breite
        double singleInnerWidth = 0.25 * radius; // Inneres Single-Feld
        double bullRadius = 0.1 * radius; // Äußeres Bullseye
        double innerBullRadius = 0.05 * radius; // Inneres Bullseye

        // Radien der Ringe definieren
        double[][] radii = {
                {outerRadius, outerRadius - doubleRingWidth}, // Double Ring
                {outerRadius - doubleRingWidth, outerRadius - doubleRingWidth - singleOuterWidth}, // Single Outer Ring
                {outerRadius - doubleRingWidth - singleOuterWidth, outerRadius - doubleRingWidth - singleOuterWidth - tripleRingWidth}, // Triple Ring
                {outerRadius - doubleRingWidth - singleOuterWidth - tripleRingWidth, bullRadius} // Single Inner Ring
        };

        // Dartscheibennummern
        int[] dartNumbers = {20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, 6, 13, 4, 18, 1};
        int[] dartIndices = {20, 5, 12, 9, 14, 11, 8, 16, 7, 19, 3, 17, 2, 15, 10, 6, 13, 4, 18, 1, 0};

        // 20 Segmente pro Ring
        for (int i = 0; i < radii.length; i++) { // Für jeden Ring
            double radiusOuter = radii[i][0];
            double radiusInner = radii[i][1];

            for (int j = 0; j < 20; j++) { // Für jedes Segment
                // Berechne Segmentwinkel
                double theta1 = Math.toRadians(j * 18 - 9 + 90);
                double theta2 = Math.toRadians((j + 1) * 18 - 9 + 90);

                int feldNummer = dartIndices[j] - 1;
                Color color;
                if (i == 0) { // Double Ring
                    color = getColorForTreffer(hitDistribution[1][feldNummer], maxHits);
                } else if (i == 1 || i == 3) { // Single Outer und Single Inner
                    color = getColorForTreffer(hitDistribution[0][feldNummer], maxHits);
                } else { // Triple Ring
                    color = getColorForTreffer(hitDistribution[2][feldNummer], maxHits);
                }

                g2d.setColor(color);
                drawSegment(g2d, centerX, centerY, radiusInner, radiusOuter, theta1, theta2);
            }
        }

        // Äußeres Bullseye
        g2d.setColor(getColorForTreffer(hitDistribution[0][20], maxHits)); // Single Bull
        g2d.fillOval(centerX - (int) bullRadius, centerY - (int) bullRadius, (int) (2 * bullRadius), (int) (2 * bullRadius));

        // Inneres Bullseye
        g2d.setColor(getColorForTreffer(hitDistribution[1][20], maxHits)); // Inner Bull
        g2d.fillOval(centerX - (int) innerBullRadius, centerY - (int) innerBullRadius, (int) (2 * innerBullRadius), (int) (2 * innerBullRadius));

        // Zahlen rund um die Dartscheibe
        for (int j = 0; j < dartNumbers.length; j++) {
            double angle = Math.toRadians(j * 18 + 90); // 9° Rotation und zusätzlich 90° verschieben
            int x = centerX + (int) (1.1 * radius * Math.cos(angle));
            int y = centerY - (int) (1.1 * radius * Math.sin(angle));
            g2d.setColor(Color.BLACK);
            g2d.drawString(String.valueOf(dartNumbers[j]), x, y);
        }
    }


    private void drawSegment(Graphics2D g2d, int centerX, int centerY, double radiusInner, double radiusOuter, double theta1, double theta2) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(centerX + radiusInner * Math.cos(theta1), centerY - radiusInner * Math.sin(theta1));
        path.lineTo(centerX + radiusOuter * Math.cos(theta1), centerY - radiusOuter * Math.sin(theta1));
        path.lineTo(centerX + radiusOuter * Math.cos(theta2), centerY - radiusOuter * Math.sin(theta2));
        path.lineTo(centerX + radiusInner * Math.cos(theta2), centerY - radiusInner * Math.sin(theta2));
        path.closePath();
        g2d.fill(path);
        g2d.setColor(Color.BLACK); // Schwarzer Rand
        g2d.draw(path);
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamedata".equals(evt.getPropertyName())) {
            GameData gameData = (GameData) evt.getNewValue();
            // Extrahiere die neuen Trefferdaten aus dem GameData-Objekt

            for (int i = 0; i < gameData.getCurrentPlayer().getStatistik().getGetroffeneFelder().size(); i++) {
                int cols = gameData.getCurrentPlayer().getStatistik().getGetroffeneFelder().get(i).size(); // Länge der aktuellen Zeile
                getroffeneFelder[i] = new int[cols];
                for (int j = 0; j < cols; j++) {
                    getroffeneFelder[i][j] = gameData.getCurrentPlayer().getStatistik().getGetroffeneFelder().get(i).get(j);
                }
            }
            // Zeichne das Dartboard neu
            this.repaint();
        }
    }
}
