import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MQTTClient implements MqttCallback {
    private String brokerUrl;
    private String clientId;
    private String topic;
    private String username;
    private String password;
    private MqttClient client;
    private GameData gameData;
    private int qos;
    private Sounds sounds;
    private MqttConnectOptions options;

    public MQTTClient(String brokerUrl, String clientId, String topic, int qos, String username, String password, GameData gameData) {
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topic = topic;
        this.username = username;
        this.password = password;
        this.gameData = gameData;
        this.qos = qos;
        this.sounds = new Sounds();

        try {
            // MQTT-Client erstellen
            client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            client.setCallback(this); // Damit die Callback Funktionen auch funktionieren
        } catch (MqttException e) {
            System.err.println("Fehler beim Erstellen des MQTT-Clients: " + e.getMessage());
        }
    }

    public void connect() {
        try {
            this.options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray()); // Passwort als char[] übergeben

            client.connect(options);
            if (isConnected() == true) {
                System.out.println("Verbunden mit dem MQTT-Broker");
            }else{
                System.out.println("MQTT Broker nicht verbunden");
            }


            // Subscriben des gewünschten Topics
            client.subscribe(topic, this.qos, (topic, message) -> {
                String payload = new String(message.getPayload());
                System.out.println("Nachricht empfangen: " + payload);

                // Aktualisierung der GameData
                gameData.updateFromMqttMessage(payload);
                System.out.println("GameData wurde aktualisiert.");
            });
            // Sound Topic abfragen
            client.subscribe("status/playSound", this.qos, (topic, message) -> {
                sounds.handleSound(topic, new String(message.getPayload()));
            });
        } catch (MqttException e) {
            System.err.println("Fehler beim Verbinden mit dem MQTT-Broker: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                System.out.println("Verbindung zum MQTT-Broker getrennt");
            }
        } catch (MqttException e) {
            System.out.println("Fehler beim Trennen der Verbindung: " + e.getMessage());
        }
    }

    public void publish(String message) {
        try {
            if (client != null && client.isConnected()) {
                client.publish(topic, new MqttMessage(message.getBytes()));
                System.out.println("Nachricht veröffentlicht: " + message);
            }
        } catch (MqttException e) {
            System.out.println("Fehler beim Veröffentlichen der Nachricht: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    // MqttCallback: Verbindungsverlust behandeln
    @Override
    public void connectionLost(Throwable cause) {
        System.out.println("Verbindung verloren: " + cause.getMessage());
        retryConnection();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        //brauchen wir nicht, da Lambda bei subscribe
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        //brauchen wir nicht
    }

    // Methode zur Wiederverbindung
    private void retryConnection() {
        new Thread(() -> {
            while (!isConnected()) {
                try {
                    System.out.println("Versuche erneut zu verbinden...");
                    client.connect(options);
                } catch (Exception e) {
                    System.err.println("Wiederverbindungsversuch fehlgeschlagen: " + e.getMessage());
                    try {
                        Thread.sleep(5000); // Warten, bevor der nächste Versuch startet
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            System.out.println("Erneut verbunden!");
        }).start();
    }
}
