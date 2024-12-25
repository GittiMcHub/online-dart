import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DartboardConnectorPanel extends JPanel {

    private JTextField dartboardMacField;
    private JTextField dartboardUuidField;
    private JComboBox<Integer> dartboardIdCombobox;
    private JLabel ausgabeText;


    public DartboardConnectorPanel(){
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel für Eingabefelder
        JPanel formPanel = new JPanel(new GridLayout(5, 2, 10, 10));

        // Felder hinzufügen
        formPanel.add(new JLabel("MAC-Adresse:"));
        dartboardMacField = new JTextField("64:CF:D9:3A:E0:C1");
        formPanel.add(dartboardMacField);

        // Felder hinzufügen
        formPanel.add(new JLabel("Bluetooth UUID:"));
        dartboardUuidField = new JTextField("0000ffe1-0000-1000-8000-00805f9b34fb");
        formPanel.add(dartboardUuidField);

        // Felder hinzufügen
        formPanel.add(new JLabel("Dartboard ID:"));
        dartboardIdCombobox = new JComboBox<>(new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        dartboardIdCombobox.setSelectedIndex(0);
        formPanel.add(dartboardIdCombobox);

        formPanel.add(new JLabel("MQTT Einstellungen wie Config Seite"));
        ausgabeText = new JLabel("Dartboard Connector noch nicht gestartet");
        formPanel.add(ausgabeText);

        add(formPanel, BorderLayout.CENTER);

        // Button erstellen
        JButton connectButton = new JButton("Dartboard Connector starten");
        add(connectButton, BorderLayout.SOUTH);

        // Button-ActionListener
        connectButton.addActionListener(e -> startDartboardConnector());
    }

    private void startDartboardConnector() {
        new Thread(() -> {
            try {
                String jarPath = "dartBlueMqttConnector-v0.1.2.py";

                List<String> command = new ArrayList<>();
                // Bestimmen des Betriebssystems
                String os = System.getProperty("os.name").toLowerCase();

                // Windows benötigt "cmd /c", andere Betriebssysteme nicht
                if (os.contains("win")) {
                    command.add("cmd");
                    command.add("/c");
                    command.add("start");
                }

                command.add("python");
                command.add(jarPath);
                command.add("--mqttbrokerip");
                command.add(GameConfigPanel.getInstance().getIpAddressField());
                command.add("--mqttbrokerport");
                command.add(GameConfigPanel.getInstance().getPortField());
                command.add("--mqttuser");
                command.add(GameConfigPanel.getInstance().getUserField());
                command.add("--mqttpassword");
                command.add(GameConfigPanel.getInstance().getPasswordField());
                command.add("--mqttqos");
                command.add(Integer.toString(GameConfigPanel.getInstance().getQosComboBox()));

                command.add("--dartboard_mac");
                command.add(dartboardMacField.getText());
                command.add("--dartboard_uuid");
                command.add(dartboardUuidField.getText());
                command.add("--dartboard_id");
                command.add(String.valueOf(dartboardIdCombobox.getSelectedItem()));


                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);

                ausgabeText.setText("Connector starten...");
                Process process = processBuilder.start();

                ausgabeText.setText("Connector gestartet.");
                int exitCode = process.waitFor();
                System.out.println("Connector exited with code: " + exitCode);
            } catch (Exception ex) {
                ex.printStackTrace();
                ausgabeText.setText("Fehler beim starten des Connectors.");
            }
        }).start();
    }

}
