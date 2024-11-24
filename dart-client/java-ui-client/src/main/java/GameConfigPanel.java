import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GameConfigPanel extends JPanel {
    private static final GameConfigPanel instance = new GameConfigPanel();
    private JTextField ipAddressField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passwordField;
    private JTextField clientIdField;
    private JComboBox<Integer> qosComboBox;
    private JLabel ausgabeText;
    private MQTTClient mqttClient;
    private JCheckBox soundCheckbox;

    public GameConfigPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel für Eingabefelder
        JPanel formPanel = new JPanel(new GridLayout(8, 2, 10, 10));

        // Felder hinzufügen
        formPanel.add(new JLabel("IP-Adresse:"));
        ipAddressField = new JTextField("127.0.0.1");
        formPanel.add(ipAddressField);

        formPanel.add(new JLabel("Port:"));
        portField = new JTextField("1883");
        formPanel.add(portField);

        formPanel.add(new JLabel("Benutzer:"));
        userField = new JTextField("dartboard");
        formPanel.add(userField);

        formPanel.add(new JLabel("Passwort:"));
        passwordField = new JPasswordField("smartness");
        formPanel.add(passwordField);

        formPanel.add(new JLabel("Client-ID:"));
        clientIdField = new JTextField("java-dart-client");
        formPanel.add(clientIdField);

        formPanel.add(new JLabel("QoS:"));
        qosComboBox = new JComboBox<>(new Integer[]{0, 1, 2});
        qosComboBox.setSelectedIndex(0); // Standardmäßig QoS 0
        formPanel.add(qosComboBox);

        formPanel.add(new JLabel("Sound:"));
        soundCheckbox = new JCheckBox("",true);
        formPanel.add(soundCheckbox);

        ausgabeText = new JLabel("Noch nicht verbunden");
        formPanel.add(ausgabeText);

        add(formPanel, BorderLayout.CENTER);

        // Button erstellen
        JButton connectButton = new JButton("Mit MQTT Broker verbinden..");
        add(connectButton, BorderLayout.SOUTH);


        // Button-ActionListener
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createMqttClient();
            }
        });
    }
    public static GameConfigPanel getInstance() {
        return instance;
    }

    private void createMqttClient() {
        String ipAddress = ipAddressField.getText();
        int port = Integer.parseInt(portField.getText());
        String user = userField.getText();
        String password = new String(passwordField.getPassword());
        String clientId = clientIdField.getText();
        int qos = (int) qosComboBox.getSelectedItem();

        this.mqttClient = new MQTTClient("tcp://" + ipAddress + ":" + port, clientId, "status/gameUpdate", qos, user, password, GameData.getInstance());

        SwingUtilities.invokeLater(() -> {
            this.getMqttClient().connect();
            if (getMqttClient().isConnected() == true) {
                ausgabeText.setText("MQTT Broker Connected!");

            } else {
                ausgabeText.setText("MQTT Broker Error");
            }
        });
    }
    public JCheckBox getSoundCheckbox() {
        return soundCheckbox;
    }
    public MQTTClient getMqttClient() {
        return mqttClient;
    }
}
