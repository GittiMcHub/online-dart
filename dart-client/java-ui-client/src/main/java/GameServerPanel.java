import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class GameServerPanel extends JPanel {
    private JTextField startPunktestandField;
    private JTextField kostenStrafpunkteField;
    private JComboBox<Integer> abzahlSpieleComboBox;
    private JComboBox<Integer> abzahlSpielerComboBox;
    private JLabel ausgabeText;

    public GameServerPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel für Eingabefelder
        JPanel formPanel = new JPanel(new GridLayout(8, 2, 10, 10));

        // Felder hinzufügen
        formPanel.add(new JLabel("Start Punktestand:"));
        startPunktestandField = new JTextField("301");
        formPanel.add(startPunktestandField);

        formPanel.add(new JLabel("Spiele im Turnier:"));
        abzahlSpieleComboBox = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        abzahlSpieleComboBox.setSelectedIndex(0); // Standardmäßig 1
        formPanel.add(abzahlSpieleComboBox);

        formPanel.add(new JLabel("Anzahl Spieler:"));
        abzahlSpielerComboBox = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        abzahlSpielerComboBox.setSelectedIndex(1); // Standardmäßig 2
        formPanel.add(abzahlSpielerComboBox);

        formPanel.add(new JLabel("Kosten pro Strafpunkt in Cent:"));
        kostenStrafpunkteField = new JTextField("50");
        formPanel.add(kostenStrafpunkteField);

        formPanel.add(new JLabel("MQTT Einstellungen wie Config Seite"));
        ausgabeText = new JLabel("Server noch nicht gestartet");
        formPanel.add(ausgabeText);

        add(formPanel, BorderLayout.CENTER);

        // Button erstellen
        JButton connectButton = new JButton("Server starten");
        add(connectButton, BorderLayout.SOUTH);

        // Button-ActionListener
        connectButton.addActionListener(e -> startDartserver());
    }

    private void startDartserver() {
        new Thread(() -> {
            try {
                String jarPath = "anthrax-v0.1.1.jar";

                List<String> command = new ArrayList<>();
                // Bestimmen des Betriebssystems
                String os = System.getProperty("os.name").toLowerCase();

                // Windows benötigt "cmd /c", andere Betriebssysteme nicht
                if (os.contains("win")) {
                    command.add("cmd");
                    command.add("/c");
                    command.add("start");
                    command.add("java");
                    command.add("-jar");
                } else if (os.contains("linux")) {
                    // Unter Linux/Raspbian: Neues Terminal öffnen
                    // Raspbian hat standardmäßig lxterminal
                    command.add("lxterminal"); // Standard-Terminal auf Raspbian
                    command.add("-e"); // Auszuführender Befehl
                    command.add("java");
                    command.add("-jar");
                } else if (os.contains("mac")) {
                    // Für Mac: Terminal öffnen
                    command.add("open");
                    command.add("-a");
                    command.add("Terminal");
                    command.add("java");
                    command.add("-jar");
                } else {
                    throw new UnsupportedOperationException("Betriebssystem nicht unterstützt.");
                }

                command.add(jarPath);
                command.add("-mqttbrokerip");
                command.add(GameConfigPanel.getInstance().getIpAddressField());
                command.add("-mqttbrokerport");
                command.add(GameConfigPanel.getInstance().getPortField());
                command.add("-mqttuser");
                command.add(GameConfigPanel.getInstance().getUserField());
                command.add("-mqttpassword");
                command.add(GameConfigPanel.getInstance().getPasswordField());
                command.add("-mqttclientid");
                command.add("java_ui_server");
                command.add("-mqttqos");
                command.add(Integer.toString(GameConfigPanel.getInstance().getQosComboBox()));
                command.add("-startpunkte");
                command.add(startPunktestandField.getText());
                command.add("-spiele");
                command.add(String.valueOf(abzahlSpieleComboBox.getSelectedItem()));
                command.add("-spieler");
                command.add(String.valueOf(abzahlSpielerComboBox.getSelectedItem()));
                command.add("-kostenstrafpunkte");
                command.add(kostenStrafpunkteField.getText());

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);

                ausgabeText.setText("Server starten...");
                Process process = processBuilder.start();

                ausgabeText.setText("Server gestartet.");
                int exitCode = process.waitFor();
                System.out.println("Dartserver exited with code: " + exitCode);
            } catch (Exception ex) {
                ex.printStackTrace();
                ausgabeText.setText("Fehler beim starten des Servers.");
            }
        }).start();
    }
}
