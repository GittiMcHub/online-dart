import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // <-------------- MQTT und Shared Memory erstellen -------------->
        SharedData sharedData = new SharedData();
        MqttHandler mqtt = new MqttHandler(sharedData);
        Thread mqttThread = new Thread(mqtt);
        mqttThread.start();
        
        // <-------------- Spieleinstellungen setzen -------------->
        Scanner scanner = new Scanner(System.in);

        System.out.println("[MAIN] Punktestand zum Start des Spiels eingeben: z.B. 301 oder 501. Frei wählbar... ");
        String str_startPunktestand = scanner.nextLine();
        int startPunktestand =  Integer.parseInt(str_startPunktestand);

        System.out.println("[MAIN] Gebe die Anzahl der Spiele dieses Turniers ein: ");
        String str_anzahlSpiele = scanner.nextLine();
        int anzahlSpiele =  Integer.parseInt(str_anzahlSpiele);

        System.out.println("[MAIN] Bitte gebe die Anzahl der Spieler ein:");
        String str_anzahlSpieler = scanner.nextLine();
        int anzahlSpieler = Integer.parseInt(str_anzahlSpieler);

        // Spieler erzeugen
        ArrayList<Spieler> spielerListe = new ArrayList<>();
        for(int i = 0; i<anzahlSpieler;i++){
            System.out.println("[MAIN] Bitte gebe den Namen für Spieler " + (i+1) + " ein:");
            String spielername = scanner.nextLine();

            System.out.println("[MAIN] Bitte gebe die ID der Dartscheibe für Spieler " + (i+1) + " ein:");
            String str_dartboardId = scanner.nextLine();
            int dartboardId = Integer.parseInt(str_dartboardId);

            spielerListe.add(new Spieler(i,spielername,dartboardId));
        }

        System.out.println("[MAIN] Bitte gebe die Kosten für einen Strafpunkt in Cent ein:");
        String str_kostenStrafpunkt = scanner.nextLine();
        int kostenStrafpunkt = Integer.parseInt(str_kostenStrafpunkt);
        scanner.close();

        // <-------------- Turnier erstellen -------------->
        ArrayList<Spiel> spiele = new ArrayList<>();

        for(int i = 0; i<anzahlSpiele;i++){
            spiele.add(new Spiel(i+1,spielerListe, sharedData, mqtt, anzahlSpiele, kostenStrafpunkt));
        }
        Turnier turnier = new Turnier(spiele);

        // Spiele nach und nach durchführen
        for (int i = 0; i < spiele.size(); i++ ){
            ArrayList<Spieler> spielerReihenfolge = new ArrayList<>();
            if(i==0){
                // Beim ersten Spiel die Reihenfolge verwenden.
                for (Spieler spieler : spielerListe){
                    spielerReihenfolge.add(spieler);
                }
            }else{
                // Bei den nächsten Spielen, beginnt der vorherige letztplatzierte
                ArrayList<Spieler> reversedPlatzierung = new ArrayList<>(spiele.get(i-1).getSpielerPlatzierung());
                Collections.reverse(reversedPlatzierung);
                for(Spieler spieler : reversedPlatzierung) {
                    spielerReihenfolge.add(spieler);
                }
            }
            // Spielerreihenfolge im jeweiligen Spiel setzen
            spiele.get(i).setSpielerReihenfolge(spielerReihenfolge);

            System.out.println("Starte Spiel Nummer " + i);
            for(Spieler spieler : spielerListe){
                spieler.setPunktestand(startPunktestand);
                spieler.resetSpieldatenStatistik();
            }
            Thread spielThread = new Thread(spiele.get(i));
            spielThread.start();

            try {
                spielThread.join(); // main-Thread wartet auf das Ende des spielThread
                System.out.println("[MAIN] Spiel-Thread ist beendet, main-Thread geht weiter.");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("[MAIN] Spiele beendet!");
    }
}