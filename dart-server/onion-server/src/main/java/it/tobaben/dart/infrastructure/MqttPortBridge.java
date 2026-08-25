package it.tobaben.dart.infrastructure;

import it.tobaben.dart.application.EngineUpdate;
import it.tobaben.dart.application.Sound;
import it.tobaben.dart.application.port.GameUpdatePublisherPort;
import it.tobaben.dart.application.port.SoundPublisherPort;

/**
 * Outbound-port implementation with a swappable MQTT target, so the
 * ServerSession is built once and survives broker reconfiguration from the
 * management UI. Also caches the last gameUpdate JSON for the web API.
 * Publishes while no adapter is attached are dropped (cannot happen during a
 * tournament: starting one requires a configured broker).
 */
public class MqttPortBridge implements GameUpdatePublisherPort, SoundPublisherPort {

    private volatile MqttAdapter target;
    private volatile String lastJson = AnthraxJsonMapper.idleUpdateJson();

    public void retarget(MqttAdapter adapter) {
        this.target = adapter;
    }

    @Override
    public void publish(EngineUpdate update) {
        this.lastJson = AnthraxJsonMapper.toJson(update);
        MqttAdapter adapter = this.target;
        if (adapter != null) {
            adapter.publish(update);
        }
    }

    @Override
    public void publishIdle() {
        this.lastJson = AnthraxJsonMapper.idleUpdateJson();
        MqttAdapter adapter = this.target;
        if (adapter != null) {
            adapter.publishIdle();
        }
    }

    @Override
    public void play(Sound sound) {
        MqttAdapter adapter = this.target;
        if (adapter != null) {
            adapter.play(sound);
        }
    }

    public String getLastJson() {
        return this.lastJson;
    }
}
