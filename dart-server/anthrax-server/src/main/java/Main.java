import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;
import org.apache.commons.cli.*;

public class Main {
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("help", false, "Zeige Hilfe");
        options.addOption("mqttbrokerip", true, "IP-Adresse MQTT Broker");
        options.addOption("mqttbrokerport", true, "port MQTT Broker");
        options.addOption("mqttuser", true, "IP-Adresse MQTT Broker");
        options.addOption("mqttpassword", true, "port MQTT Broker");
        options.addOption("mqttclientid", true, "MQTT Client ID");
        options.addOption("mqttqos", true, "MQTT Quality of Service");
        options.addOption("spieler", true, "Anzahl Spieler");
        options.addOption("spiele", true, "Anzahl Spiele");
        options.addOption("startpunkte", true, "Startpunktestand");
        options.addOption("kostenstrafpunkte", true, "Kosten pro Strafpunkt in Cent");

        // Parsen der Befehlszeilenargumente
        // Wenn ohne parameter aufgerufen, dann MQTT Daten über Configfile und Spielparameter per CLI
        // Wenn mqtt mit parameter, dann müssen alle mqtt params gesetzt werden!
        // namen der Spieler und zugehöriger DartboardID immernoch über CLI.
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            // <-------------- MQTT und Shared Memory erstellen -------------->
            SharedData sharedData = new SharedData();
            MqttHandler mqtt;
            if (cmd.hasOption("mqttbrokerip") && cmd.hasOption("mqttbrokerport") && cmd.hasOption("mqttuser") && cmd.hasOption("mqttpassword") && cmd.hasOption("mqttclientid") && cmd.hasOption("mqttqos")) {
                mqtt = new MqttHandler(sharedData, cmd.getOptionValue("mqttbrokerip"), cmd.getOptionValue("mqttbrokerport"),cmd.getOptionValue("mqttuser"),cmd.getOptionValue("mqttpassword"),cmd.getOptionValue("mqttclientid"),Integer.parseInt(cmd.getOptionValue("mqttqos")));
            }else{
                mqtt = new MqttHandler(sharedData);
            }
            Thread mqttThread = new Thread(mqtt);
            mqttThread.start();

            // <-------------- Spieleinstellungen setzen -------------->
            Scanner scanner = new Scanner(System.in);

            // Startpunkte
            int startPunktestand = 0;
            if (cmd.hasOption("startpunkte")) {
                System.out.println("[MAIN] Punktestand zum Start des Spiels: " + cmd.getOptionValue("startpunkte"));
                startPunktestand = Integer.parseInt(cmd.getOptionValue("startpunkte"));
            }else{
                System.out.println("[MAIN] Punktestand zum Start des Spiels eingeben: z.B. 301 oder 501. Frei wählbar... ");
                String str_startPunktestand = scanner.nextLine();
                startPunktestand =  Integer.parseInt(str_startPunktestand);
            }

            // Spiele
            int anzahlSpiele = 0;
            if (cmd.hasOption("spiele")) {
                System.out.println("[MAIN] Anzahl der Spiele dieses Turniers: " + cmd.getOptionValue("spiele"));
                anzahlSpiele =  Integer.parseInt(cmd.getOptionValue("spiele"));
            }else {
                System.out.println("[MAIN] Gebe die Anzahl der Spiele dieses Turniers ein: ");
                String str_anzahlSpiele = scanner.nextLine();
                anzahlSpiele =  Integer.parseInt(str_anzahlSpiele);
            }

            // Spieler
            int anzahlSpieler = 0;
            if (cmd.hasOption("spieler")) {
                System.out.println("[MAIN] Anzahl der Spieler: " + cmd.getOptionValue("spieler"));
                anzahlSpieler =  Integer.parseInt(cmd.getOptionValue("spieler"));
            }else {
                System.out.println("[MAIN] Gebe die Anzahl der Spieler ein: ");
                String str_anzahlSpieler = scanner.nextLine();
                anzahlSpieler =  Integer.parseInt(str_anzahlSpieler);
            }

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

            // Strafpunkte Kosten
            int kostenStrafpunkt = 0;
            if (cmd.hasOption("kostenstrafpunkte")) {
                System.out.println("[MAIN] Kosten für einen Strafpunkt in Cent sind: " + cmd.getOptionValue("kostenstrafpunkte"));
                kostenStrafpunkt = Integer.parseInt(cmd.getOptionValue("kostenstrafpunkte"));
            }else{
                System.out.println("[MAIN] Bitte gebe die Kosten für einen Strafpunkt in Cent ein:");
                String str_kostenStrafpunkt = scanner.nextLine();
                kostenStrafpunkt = Integer.parseInt(str_kostenStrafpunkt);
            }

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
        }catch (Exception ex){
            System.err.println("Fehler beim Parsen der Befehlszeilenargumente: " + ex.getMessage());
        }
        System.out.println("[MAIN] Spiele beendet!");
    }
}