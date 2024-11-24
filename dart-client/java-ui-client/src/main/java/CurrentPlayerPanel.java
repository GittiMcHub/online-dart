import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class CurrentPlayerPanel extends JPanel implements PropertyChangeListener {
    private JLabel nameLabel;
    private JLabel punktestandLabel;
    private JLabel letzterwurfLabel;

    public CurrentPlayerPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);

        nameLabel = new JLabel("NAME XYZ", SwingConstants.CENTER);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Helvetica", Font.BOLD, 50));

        punktestandLabel = new JLabel("???", SwingConstants.CENTER);
        punktestandLabel.setForeground(Color.WHITE);
        punktestandLabel.setFont(new Font("Helvetica", Font.BOLD, 250));

        letzterwurfLabel = new JLabel("Letzter Wurf: xxx Ges. ccc", SwingConstants.CENTER);
        letzterwurfLabel.setForeground(Color.WHITE);
        letzterwurfLabel.setFont(new Font("Helvetica", Font.PLAIN, 50));

        add(nameLabel, BorderLayout.NORTH);
        add(punktestandLabel, BorderLayout.CENTER);
        add(letzterwurfLabel, BorderLayout.SOUTH);

        // Registrieren für Änderungen in GameData
        GameData.getInstance().addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("gamedata".equals(evt.getPropertyName())) {
            GameData gameData = (GameData) evt.getNewValue();
            // Update des Namens
            nameLabel.setText(String.valueOf(gameData.getCurrentPlayer().getName()));
            // Update des Punktestands
            punktestandLabel.setText(String.valueOf(gameData.getCurrentPlayer().getPunktestand()));
            // Update des letzten Wurfs
            int letzterWurf = gameData.getLetzterWurf();
            int gesamt = gameData.getPunkteSpielzug() + letzterWurf; // Berechnung der Gesamtsumme
            letzterwurfLabel.setText("Letzter Wurf: " + letzterWurf + " Ges. " + gesamt);
        }
    }
}