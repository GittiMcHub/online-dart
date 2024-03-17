import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SharedData {
    private String receivedMessage;
    private int dartboardId;
    private AtomicLong messageId = new AtomicLong();

    public synchronized void setMessage(MqttMessage message, int dartboardId) {
        //System.out.println("[SharedData] setMessage Beginn");
        String payload = "";
        try {
            payload = new String(message.getPayload(), "UTF-8");
            //System.out.println("[SharedData] Payload ist: " + payload);
        }catch (Exception ex){
            ex.printStackTrace();
        }
        //System.out.println("[SharedData] setze receivedMessage");
        this.receivedMessage = payload;
        //System.out.println("[SharedData] increment Message ID");
        this.messageId.incrementAndGet(); // Inkrementiert die Versionsnummer
        //System.out.println("[SharedData] setze dartboard ID");
        this.dartboardId = dartboardId;
        //System.out.println("[SharedData] Dartboard id was: " + this.dartboardId);
        notifyAll(); // Benachrichtigt wartende Threads
    }

    public synchronized String waitForMessage(long lastMessageId, int dartboardId) throws InterruptedException {
        // Erst die Nachricht an den Spielthread senden, wenn
        // 1. eine aktuelle Nachricht reinkommt (verhindern, dass eine alte Nachricht als Wert genutzt wird)
        // 2. nur die Dartscheibe des aktuellen Spielers auch eine Message gesendet hat
        while (messageId.get() <= lastMessageId || dartboardId != this.dartboardId) {
            wait(); // Wartet auf die Benachrichtigung
        }
        return receivedMessage;
    }

    public synchronized String waitForMessage(long lastMessageId) throws InterruptedException {
        // Erst die Nachricht an den Spielthread senden, wenn
        // 1. eine aktuelle Nachricht reinkommt (verhindern, dass eine alte Nachricht als Wert genutzt wird)
        // 2. nur die Dartscheibe des aktuellen Spielers auch eine Message gesendet hat
        while (messageId.get() <= lastMessageId) {
            wait(); // Wartet auf die Benachrichtigung
        }
        return receivedMessage;
    }

    public synchronized long getLastMessageId() {
        return messageId.get();
    }
}
