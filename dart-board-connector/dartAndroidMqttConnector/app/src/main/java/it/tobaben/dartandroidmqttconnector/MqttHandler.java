package it.tobaben.dartandroidmqttconnector;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import android.content.Context;
import android.widget.EditText;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

public class MqttHandler implements Runnable {
    private final String url;
    private final String user;
    private final String pw;
    private final String dartboardId;
    private final String clientId;
    private final MqttClient mqttClient;
    private final MemoryPersistence persistence;
    private final MqttConnectOptions connectOptions;
    private boolean running;
    private final String topic;
    private MainActivity mainActivity;
    private TextView prompt;
    public MqttHandler(String url, String user, String pw, String dartboardId, MainActivity activity, TextView textViewPrompt){
        this.url = url;
        this.user = user;
        this.pw = pw;
        this.dartboardId = dartboardId;
        this.clientId = "android-dartboard-connector" + this.dartboardId;
        this.topic = "dartboard/" + dartboardId;
        this.mainActivity = activity;
        this.prompt = textViewPrompt;

        this.persistence = new MemoryPersistence();
        this.connectOptions = new MqttConnectOptions();
        connectOptions.setUserName(this.user);
        connectOptions.setPassword(this.pw.toCharArray());
        this.running = true;
        try {
            this.mqttClient = new MqttClient(url,clientId,persistence);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (this.running){
            if(this.mqttClient.isConnected()){
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    //
                }
            }else{
                try {
                    updatePrompt("MQTT TryToConnect");
                    this.mqttClient.connect(this.connectOptions);
                    updatePrompt("MQTT Connected");
                } catch (MqttException e) {
                    updatePrompt("MQTT Error");
                }
            }
        }
    }

    public void stop(){
        this.running=false;
        try {
            this.mqttClient.disconnect();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    public void publish(String msg){
        MqttMessage message = new MqttMessage(msg.getBytes());
        try {
            mqttClient.publish(this.topic, message);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    private void updatePrompt(final String msg){
        mainActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                prompt.append(msg);
                prompt.append(";");
            }
        });
    }
}
