import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DartboardConnectorPanel extends JPanel {

    private JTextField dartboardMacField;
    private JTextField dartboardUuidField;
    private JComboBox<Integer> dartboardIdCombobox;
    private JLabel ausgabeText;

    private JLabel macAddressFieldLabel;
    private JCheckBox usePythonCheckbox;


    public DartboardConnectorPanel(){
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel für Eingabefelder
        JPanel formPanel = new JPanel(new GridLayout(7, 2, 10, 10));

        // Felder hinzufügen
        formPanel.add(new JLabel("MAC-Adresse:"));
        dartboardMacField = new JTextField("64:CF:D9:3A:E0:C1");
        formPanel.add(dartboardMacField);
        // Button erstellen
        macAddressFieldLabel = new JLabel("MAC-Adresse suchen:");
        formPanel.add(macAddressFieldLabel);
        JButton discoverButton = new JButton("MAC-Adresse Dartboard suchen");
        formPanel.add(discoverButton);

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
        ausgabeText = new JLabel("Dartboard Connector noch nicht gestartet.");
        formPanel.add(ausgabeText);

        formPanel.add(new JLabel("(Nur bei Windows) Nutze Python-Skript statt Exe"));
        usePythonCheckbox = new JCheckBox("", false);
        formPanel.add(usePythonCheckbox);

        add(formPanel, BorderLayout.CENTER);

        // Button erstellen
        JButton connectButton = new JButton("Dartboard Connector starten (Dartboard LED muss Rot sein)");
        add(connectButton, BorderLayout.SOUTH);

        // Button-ActionListener
        connectButton.addActionListener(e -> startDartboardConnector());
        discoverButton.addActionListener(e -> discoverDartboard());
    }

    private void startDartboardConnector() {
        new Thread(() -> {
            try {
                String connectorSkriptPath;

                List<String> command = new ArrayList<>();
                // Bestimmen des Betriebssystems
                String os = System.getProperty("os.name").toLowerCase();

                // Windows benötigt "cmd /c", andere Betriebssysteme nicht
                if (os.contains("win")) {
                    command.add("cmd");
                    command.add("/c");
                    command.add("start");
                    // Wenn python benutzt werden soll
                    if(usePythonCheckbox.isSelected()){
                        command.add("python");
                        connectorSkriptPath = "dartBlueMqttConnector-v0.1.2.py";
                    }else{
                        // Für Windows PCs ohne Python. Alle Abhängigkeiten in exe Datei enthalten
                        connectorSkriptPath = "dartBlueMqttConnector-v0.1.2.exe";
                    }
                }else {
                    // Andere Betriebssysteme brauchen zusätzlich python inkl. Abhängigkeiten
                    command.add("python");
                    connectorSkriptPath = "dartBlueMqttConnector-v0.1.2.py";
                }

                command.add(connectorSkriptPath);
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

                // Shutdown-Hook hinzufügen
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (process.isAlive()) {
                        process.destroy(); // Prozess beenden
                        System.out.println("Connector-Prozess beendet.");
                    }
                }));

                ausgabeText.setText("Connector gestartet.");
                int exitCode = process.waitFor();
                System.out.println("Connector exited with code: " + exitCode);
            } catch (Exception ex) {
                ex.printStackTrace();
                ausgabeText.setText("Fehler beim starten des Connectors.");
            }
        }).start();
    }
    private void discoverDartboard() {
        new Thread(() -> {
            try {
                macAddressFieldLabel.setText("Suche MAC-Adresse Dartboard...");
                String findDeviceSkriptPath;
                String outputFilePath = "findDevices.log";

                List<String> command = new ArrayList<>();
                // Bestimmen des Betriebssystems
                String os = System.getProperty("os.name").toLowerCase();

                if (os.contains("win")) {
                    // Für Windows
                    command.add("cmd");
                    command.add("/c");

                    // Wenn python benutzt werden soll
                    if(usePythonCheckbox.isSelected()){
                        command.add("python");
                        findDeviceSkriptPath = "findDevices-v0.1.2.py";
                    }else{
                        // Für Windows PCs ohne Python. Alle Abhängigkeiten in exe Datei enthalten
                        findDeviceSkriptPath = "findDevices-v0.1.2.exe";
                    }
                    command.add(findDeviceSkriptPath);
                    command.add(">");
                    command.add(outputFilePath); // Ausgabe in die Datei umleiten
                } else {
                    // Für Linux/MacOS
                    // Andere Betriebssysteme brauchen zusätzlich python inkl. Abhängigkeiten
                    findDeviceSkriptPath = "findDevices-v0.1.2.py";

                    command.add("bash");
                    command.add("-c");
                    command.add(String.format("python %s > %s", findDeviceSkriptPath, outputFilePath)); // Ausgabe in die Datei umleiten
                }

                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.redirectErrorStream(true);

                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                System.out.println("Connector exited with code: " + exitCode);
                String macAddress = getMacAddressFromScriptOutput();
                if(exitCode == 0 && !macAddress.isEmpty()){
                    macAddressFieldLabel.setText("MAC-Adresse gefunden: " + macAddress);
                    dartboardMacField.setText(macAddress);
                }else{
                    macAddressFieldLabel.setText("Fehler beim Suchen der MAC-Adresse:");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    public String getMacAddressFromScriptOutput() {
        String filePath = "findDevices.log"; // Die Datei mit den Ausgaben
        String targetDevice = "Smartness1";    // Das Zielgerät
        String macAddress = "";

        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("Die Datei " + filePath + " wurde nicht gefunden.");
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(targetDevice)) {
                    // Extrahiere die MAC-Adresse
                    macAddress = line.split(": ")[0].trim();
                    System.out.println("Die MAC-Adresse von " + targetDevice + " ist: " + macAddress);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return macAddress;
    }

}
