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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
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
    private final List<TopicHandler> handlers = new CopyOnWriteArrayList<>();
    private String willTopic;
    private String willPayload;

    private record TopicHandler(String topicFilter, int qos, BiConsumer<String, String> handler) {
    }

    public MqttAdapter(MqttConfig config, BlockingQueue<DartboardInput> inputQueue) throws MqttException {
        this.config = config;
        this.inputQueue = inputQueue;
        // MemoryPersistence: paho's default file persistence would litter the
        // working directory with a <clientId>-<brokerUrl>/.lck folder; the
        // server republishes the full game state anyway, so in-flight QoS
        // messages are not worth persisting across crashes.
        System.out.println("MQTT ClientId = " + config.clientId());
        this.client = new MqttClient(config.brokerUrl(), config.clientId(), new MemoryPersistence());
        this.client.setCallback(this);
    }

    /** Sets the MQTT Last-Will published by the broker if this client dies. Call before connect(). */
    public void setLastWill(String topic, String payload) {
        this.willTopic = topic;
        this.willPayload = payload;
    }

    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(this.config.username());
        options.setPassword(this.config.password().toCharArray());
        options.setKeepAliveInterval(10);
        options.setAutomaticReconnect(true);
        if (this.willTopic != null) {
            options.setWill(this.willTopic, this.willPayload.getBytes(StandardCharsets.UTF_8), 1, false);
        }
        System.out.println("[MQTT] Verbinde mit " + this.config.brokerUrl());
        this.client.connect(options);
    }

    public void disconnect() throws MqttException {
        if (this.client.isConnected()) {
            this.client.disconnect();
        }
    }

    public boolean isConnected() {
        return this.client.isConnected();
    }

    /**
     * Registers an additional subscription (e.g. lobby topics). The filter is
     * subscribed on every (re)connect; matching messages are dispatched to the
     * handler with (topic, payload). Handlers run on paho's callback thread and
     * must not block.
     */
    public void registerHandler(String topicFilter, int qos, BiConsumer<String, String> handler) {
        this.handlers.add(new TopicHandler(topicFilter, qos, handler));
        if (this.client.isConnected()) {
            try {
                this.client.subscribe(topicFilter, qos);
            } catch (MqttException e) {
                System.err.println("[MQTT] Subscribe auf " + topicFilter + " fehlgeschlagen: " + e.getMessage());
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        System.out.println("[MQTT] " + (reconnect ? "Wieder verbunden" : "Verbunden") + " mit " + serverURI);
        try {
            this.client.subscribe(INPUT_TOPIC, this.config.qos());
            for (TopicHandler registered : this.handlers) {
                this.client.subscribe(registered.topicFilter(), registered.qos());
            }
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
        for (TopicHandler registered : this.handlers) {
            if (topicMatches(registered.topicFilter(), topic)) {
                registered.handler().accept(topic, payload);
            }
        }
        int dartboardId = extractDartboardId(topic);
        if (dartboardId < 0) {
            return;
        }
        Optional<DartboardInput> input = SegmentCodec.decode(dartboardId, payload);
        if (input.isPresent()) {
            System.out.println("[MQTT] Wurf-Eingang " + topic + ": " + payload);
            this.inputQueue.offer(input.get());
        } else {
            System.out.println("[MQTT] Ungültige Nachricht verworfen: '" + payload + "' auf " + topic);
        }
    }

    /** Minimal MQTT filter matching: exact topics, a trailing "#" and "+" levels. */
    static boolean topicMatches(String filter, String topic) {
        String[] filterLevels = filter.split("/");
        String[] topicLevels = topic.split("/");
        for (int i = 0; i < filterLevels.length; i++) {
            if ("#".equals(filterLevels[i])) {
                return true;
            }
            if (i >= topicLevels.length) {
                return false;
            }
            if (!"+".equals(filterLevels[i]) && !filterLevels[i].equals(topicLevels[i])) {
                return false;
            }
        }
        return filterLevels.length == topicLevels.length;
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
    public void publishIdle() {
        publishMessage(GAME_UPDATE_TOPIC, AnthraxJsonMapper.idleUpdateJson(), 1);
    }

    /** Generic publish for adapters built on top of this connection (lobby, throw forwarding). */
    public void publish(String topic, String payload, int qos, boolean retained) {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(qos);
        message.setRetained(retained);
        try {
            this.client.publish(topic, message);
        } catch (MqttException e) {
            System.err.println("[MQTT] Publish auf " + topic + " fehlgeschlagen: " + e.getMessage());
        }
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
