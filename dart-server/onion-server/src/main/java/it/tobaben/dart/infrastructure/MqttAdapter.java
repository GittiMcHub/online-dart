package it.tobaben.dart.infrastructure;

import com.google.gson.JsonObject;
import it.tobaben.dart.application.DartboardInput;
import it.tobaben.dart.application.EngineUpdate;
import it.tobaben.dart.application.Sound;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MQTT adapter (paho): subscribes to dartboard/# and feeds decoded inputs into
 * the engine queue; implements the outbound ports by publishing
 * status/gameUpdate (QoS 1) and status/playSound (QoS 2). Reconnects
 * automatically and resubscribes on reconnect (anthrax needed a manual retry
 * thread for this).
 */
public class MqttAdapter implements MqttCallbackExtended, GameUpdatePublisherPort, SoundPublisherPort {

    public static final String INPUT_TOPIC = "dartboard/#";
    public static final String GAME_UPDATE_TOPIC = "status/gameUpdate";
    public static final String SOUND_TOPIC = "status/playSound";

    private static final Pattern DARTBOARD_TOPIC = Pattern.compile("dartboard/(\\d+)");

    private final MqttConfig config;
    private final BlockingQueue<DartboardInput> inputQueue;
    private final MqttClient client;

    public MqttAdapter(MqttConfig config, BlockingQueue<DartboardInput> inputQueue) throws MqttException {
        this.config = config;
        this.inputQueue = inputQueue;
        // MemoryPersistence: paho's default file persistence would litter the
        // working directory with a <clientId>-<brokerUrl>/.lck folder; the
        // server republishes the full game state anyway, so in-flight QoS
        // messages are not worth persisting across crashes.
        this.client = new MqttClient(config.brokerUrl(), config.clientId(), new MemoryPersistence());
        this.client.setCallback(this);
    }

    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(this.config.username());
        options.setPassword(this.config.password().toCharArray());
        options.setKeepAliveInterval(10);
        options.setAutomaticReconnect(true);
        System.out.println("[MQTT] Verbinde mit " + this.config.brokerUrl());
        this.client.connect(options);
    }

    public void disconnect() throws MqttException {
        this.client.disconnect();
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        System.out.println("[MQTT] " + (reconnect ? "Wieder verbunden" : "Verbunden") + " mit " + serverURI);
        try {
            this.client.subscribe(INPUT_TOPIC, this.config.qos());
        } catch (MqttException e) {
            System.err.println("[MQTT] Subscribe fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.err.println("[MQTT] Verbindung verloren: " + cause.getMessage() + " - reconnecte...");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        int dartboardId = extractDartboardId(topic);
        if (dartboardId < 0) {
            return;
        }
        Optional<DartboardInput> input = SegmentCodec.decode(dartboardId, payload);
        if (input.isPresent()) {
            this.inputQueue.offer(input.get());
        } else {
            System.out.println("[MQTT] Ungültige Nachricht verworfen: '" + payload + "' auf " + topic);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    /** @return the dartboard id from a "dartboard/<id>" topic, or -1 */
    public static int extractDartboardId(String topic) {
        Matcher matcher = DARTBOARD_TOPIC.matcher(topic);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public void publish(EngineUpdate update) {
        publishMessage(GAME_UPDATE_TOPIC, AnthraxJsonMapper.toJson(update), 1);
    }

    @Override
    public void play(Sound sound) {
        publishMessage(SOUND_TOPIC, soundJson(sound), 2);
    }

    public static String soundJson(Sound sound) {
        JsonObject json = new JsonObject();
        json.addProperty("sound", sound.name());
        return json.toString();
    }

    private void publishMessage(String topic, String payload, int qos) {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        try {
            this.client.publish(topic, message);
        } catch (MqttException e) {
            System.err.println("[MQTT] Publish auf " + topic + " fehlgeschlagen: " + e.getMessage());
        }
    }
}
