package it.tobaben.dart.infrastructure.ble;

import it.tobaben.dart.application.board.port.ThrowPublisherPort;
import it.tobaben.dart.infrastructure.MqttAdapter;

import java.util.function.Supplier;

/**
 * Publishes board wire codes on dartboard/&lt;id&gt; with QoS 2 — through the
 * broker in every mode, so the server has exactly one input path and referee
 * buttons / Python connectors mix in naturally. The adapter is looked up per
 * publish because broker reconfiguration swaps it.
 */
public class MqttThrowForwarder implements ThrowPublisherPort {

    private final Supplier<MqttAdapter> mqtt;

    public MqttThrowForwarder(Supplier<MqttAdapter> mqtt) {
        this.mqtt = mqtt;
    }

    @Override
    public void publishThrow(int dartboardId, String wireCode) {
        MqttAdapter adapter = this.mqtt.get();
        if (adapter == null) {
            System.err.println("[BLE] Wurf verworfen (kein Broker verbunden): " + wireCode);
            return;
        }
        adapter.publish("dartboard/" + dartboardId, wireCode, 2, false);
    }
}
