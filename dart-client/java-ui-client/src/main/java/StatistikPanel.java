import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

public class StatistikPanel extends JPanel implements PropertyChangeListener {
    private List<Player> spielerReihenfolge;

    public StatistikPanel() {
        this.spielerReihenfolge = GameData.getInstance().getSpielerReihenfolge();
        setBackground(Color.BLACK);

        // Registrierung als Listener für Änderungen in GameData
        GameData.getInstance().addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamedata".equals(evt.getPropertyName())) {
            this.spielerReihenfolge = GameData.getInstance().getSpielerReihenfolge();
            revalidate();
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (spielerReihenfolge == null || spielerReihenfolge.isEmpty()) {
            return;
        }
        drawStatistiken((Graphics2D) g);
    }

    private void drawStatistiken(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int panelWidth = getWidth();
        int x = 10; // Startposition x
        int y = 10; // Startposition y
        int padding = 10; // Abstand zwischen Feldern
        int fieldWidth = 375; // Breite eines Statistikfeldes
        int fieldHeight = 450; // Höhe eines Statistikfeldes

        for (Player player : spielerReihenfolge) {
            // Zeichne das Statistikfeld
            drawPlayerStatistik(g2d, player, x, y, fieldWidth, fieldHeight);

            // Aktualisiere die Position für das nächste Feld
            x += fieldWidth + padding;

            // Prüfe, ob das nächste Feld über den Rand hinausgeht
            if (x + fieldWidth > panelWidth) {
                x = 10; // Zurück zum linken Rand
                y += fieldHeight + padding; // Neue Zeile
            }
        }
    }

    private void drawPlayerStatistik(Graphics2D g2d, Player player, int x, int y, int width, int height) {
        // Zeichne den Rahmen des Statistikfeldes
        g2d.setColor(Color.WHITE);
        g2d.drawRect(x, y, width, height);

        // Zeichne den Hintergrund
        g2d.setColor(new Color(30, 30, 30));
        g2d.fillRect(x + 1, y + 1, width - 1, height - 1);

        // Setze die Schriftart und Farbe
        g2d.setColor(Color.ORANGE);
        g2d.setFont(new Font("Helvetica", Font.BOLD, 32));
        g2d.drawString(player.getName(), x + 10, y + 30);

        g2d.setColor(Color.WHITE);
        // Zeichne Statistiken des Spielers
        g2d.setFont(new Font("Helvetica", Font.PLAIN, 20));
        Statistik statistik = player.getStatistik();

        int textY = y + 50; // Startposition für Text innerhalb des Feldes
        int textAbstand = 20;
        int zeile = 0;
        g2d.drawString("DartboardID: " + player.getDartboardId(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Avg Spiel: " + statistik.getAvgSpiel(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Avg Turnier: " + statistik.getAvgTurnier(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Max-Punkte pro Spielzug: " + statistik.getMaxPunkteProSpielzug(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Highest Finish: " + statistik.getHighestFinish(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Minimal-Anzahl Würfe bis Spielende: " + statistik.getAnzMinWuerfeBisSpielende(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Überworfen: " + statistik.getAnzUeberworfen(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Single-Feld: " + statistik.getAnzEinerFeld(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Double-Feld: " + statistik.getAnzDoubleFeld(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Triple-Feld: " + statistik.getAnzTripleFeld(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Würfe Spiel: " + statistik.getAnzWuerfeSpiel(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Bull: " + statistik.getAnzBull(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Bullseye: " + statistik.getAnzBullseye(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Randtreffer: " + statistik.getAnzRandTreffer(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Wandtreffer: " + statistik.getAnzWandTreffer(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Strafpunkte: " + statistik.getAnzStrafpunkte(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Würfe Spiel: " + statistik.getAnzWuerfeSpiel(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Anzahl Würfe Turnier: " + statistik.getAnzWuerfeTurnier(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Summe Würfe Spiel: " + statistik.getSummeSpiel(), x + 10, textY + textAbstand * zeile++);
        g2d.drawString("Summe Würfe Turnier: " + statistik.getSummeTurnier(), x + 10, textY + textAbstand * zeile++);
    }

    @Override
    public Dimension getPreferredSize() {
        // Berechne die Gesamthöhe basierend auf der Anzahl der Spieler
        int panelWidth = 800; // Mindestbreite des Panels
        int fieldWidth = 375; // Breite eines Statistikfeldes
        int fieldHeight = 450; // Höhe eines Statistikfeldes
        int padding = 10; // Abstand zwischen Feldern
        int fieldsPerRow = panelWidth / (fieldWidth + padding);

        int rows = (int) Math.ceil((double) spielerReihenfolge.size() / fieldsPerRow);
        int panelHeight = rows * (fieldHeight + padding) + padding;

        return new Dimension(panelWidth, panelHeight);
    }
}