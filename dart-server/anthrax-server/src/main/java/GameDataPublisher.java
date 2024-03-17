import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;

public class GameDataPublisher {
    private Spiel spiel;

    public GameDataPublisher(Spiel spiel){
        this.spiel = spiel;
    }

    public void publishGameData(){
        Gson gson = new Gson();
        String spielJson = gson.toJson(SpielMapper.map(this.spiel));
        System.out.println("[GamePublisher] Publish JSON Message:");
        System.out.println("[GamePublisher] "+spielJson);
        spiel.getMqttHandler().publish(spielJson);
    }
}
