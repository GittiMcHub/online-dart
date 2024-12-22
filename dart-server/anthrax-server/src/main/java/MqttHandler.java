import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MqttHandler implements Runnable {
    private final SharedData data;
    private MqttClient client;
    private MqttConnectOptions options;
    private String topic;
    private int qos;
    private ConfigReader configReader;

    public MqttHandler(SharedData sharedData){
        this.data = sharedData;
        this.configReader = new ConfigReader();

        try {
            client = new MqttClient(configReader.getBroker(), configReader.getClientID());
            this.options = new MqttConnectOptions();
            this.options.setAutomaticReconnect(true);
            this.options.setPassword(configReader.getPassword().toCharArray());
            this.options.setUserName(configReader.getUser());
            this.qos = configReader.getQos();
            this.topic = configReader.getTopic();
        } catch (MqttException e) {
            System.out.println("[MQTT-Handler] ERROR im Konstruktor");
            throw new RuntimeException(e);
        }
    }
    public MqttHandler(SharedData sharedData, String mqttbrokerIp, String mqttbrokerPort, String mqttUser, String mqttPassword,  String mqttClientId, int qos){
        this.data = sharedData;
        String broker = "tcp://" + mqttbrokerIp + ":" + mqttbrokerPort;
        try {
            client = new MqttClient(broker, mqttClientId);
            this.options = new MqttConnectOptions();
            this.options.setAutomaticReconnect(true);
            this.options.setPassword(mqttPassword.toCharArray());
            this.options.setUserName(mqttUser);
            this.qos = qos;
            this.topic = "dartboard/#";
        } catch (MqttException e) {
            System.out.println("[MQTT-Handler] ERROR im Konstruktor");
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("[MQTT-Handler] Verbinde mit MQTT Broker");
            this.client.connect(this.options);

            if (client.isConnected()) {
                System.out.println("[MQTT-Handler] Verbindung zum MQTT Broker hergestellt");
                client.setCallback(new MqttCallback() {
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        System.out.println("[MQTT-Handler] topic: " + topic + " QOS: " + message.getQos() + " Message Länge: " + message.getPayload().length);
                        System.out.println("[MQTT-Handler] message content: " + new String(message.getPayload()));
                        String payload = new String(message.getPayload(), "UTF-8");
                        // Falls irgendwelche anderen Nachrichten reinkommen, maximale Länge ist 3 Chars!
                        if(payload.length()==3){
                            int topicnumber = extractNumberFromTopic(topic);
                            data.setMessage(message, topicnumber);
                        }else{
                            System.out.println("[MQTT-Handler] Ungültige Nachricht empfangen");
                        }
                    }

                    public void connectionLost(Throwable cause) {
                        System.out.println("[MQTT-Handler] connectionLost: " + cause.getMessage());
                    }
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        System.out.println("[MQTT-Handler] deliveryComplete: " + token.isComplete());
                    }
                });
            }else{
                System.out.println("[MQTT-Handler] Not Connected to MQTT Broker!");
            }
            client.subscribe(this.topic, this.qos);

        }catch (Exception ex){
            ex.printStackTrace();
            System.out.println("[MQTT-Handler] ERROR in run()");
        }
    }

    public MqttClient getClient() {
        return client;
    }

    public static int extractNumberFromTopic(String topic) {
        // Checken ob auch tatsächlich Zahlen am Ende sind
        Pattern pattern = Pattern.compile("dartboard/(\\d+)");
        Matcher matcher = pattern.matcher(topic);
        String[] parts = topic.split("/");
        if (parts.length >= 2) {
            System.out.println("[MQTT-Handler] extractNumberFromTopic topic: " + topic);
            System.out.println("[MQTT-Handler] extractNumberFromTopic part1: " + parts[0]);
            System.out.println("[MQTT-Handler] extractNumberFromTopic part2: " + parts[1]);
            if (matcher.find()) {
                try {
                    return Integer.valueOf(matcher.group(1));
                } catch (NumberFormatException e) {
                    // Handle the case where the second part is not a valid integer
                    e.printStackTrace();
                }
            }
        }
        // Return a default value or handle the case where the topic doesn't match the expected format
        return -1;
    }

    public void publish(String pubMessage){
        MqttMessage msg = new MqttMessage();
        msg.setQos(1);
        msg.setPayload(pubMessage.getBytes());
        try {
            this.client.publish("status/gameUpdate", msg);
        } catch (Exception e) {
            e.printStackTrace();  // Handle or log the exception appropriately
        }
    }

    public void publishSound(Sounds soundName){
        Gson gson = new Gson();
        String spielJson = gson.toJson(new Sound(soundName.toString()));
        MqttMessage msg = new MqttMessage();
        msg.setQos(2);
        msg.setPayload(spielJson.getBytes());
        try {
            this.client.publish("status/playSound", msg);
        } catch (Exception e) {
            e.printStackTrace();  // Handle or log the exception appropriately
        }
    }
    // Klasse für das JSON-Format definieren
    static private class Sound {
        String sound;

        Sound(String sound) {
            this.sound = sound;
        }
    }
}
