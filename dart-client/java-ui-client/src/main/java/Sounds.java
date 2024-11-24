import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Sounds {

    private final Map<String, String> soundFiles;
    private final Map<String, String> gifFiles;
    private final JFrame gifFrame;

    public Sounds() {
        // Map für die Zuordnung von Sounds zu Dateipfaden
        soundFiles = new HashMap<>();
        soundFiles.put("WINNER", "sounds/winner.wav");
        soundFiles.put("TREFFER", "sounds/treffer.wav");
        soundFiles.put("DOUBLE", "sounds/double.wav");
        soundFiles.put("TRIPLE", "sounds/triple.wav");
        soundFiles.put("BULLSEYE", "sounds/bullseye.wav");
        soundFiles.put("RESET", "sounds/reset.wav");
        soundFiles.put("SPIELSTART", "sounds/spielstart.wav");
        soundFiles.put("STRAFE", "sounds/strafe.wav");
        soundFiles.put("UEBERWORFEN", "sounds/ueberworfen.wav");
        soundFiles.put("MAXPOINTS", "sounds/180.wav");

        // Map für die Zuordnung von GIFs zu Dateipfaden
        gifFiles = new HashMap<>();
        gifFiles.put("WINNER", "gifs/winner.gif");
        gifFiles.put("BULLSEYE", "gifs/bull.gif");
        gifFiles.put("STRAFE", "gifs/strafe.gif");
        gifFiles.put("UEBERWORFEN", "gifs/ueberworfen.gif");
        gifFiles.put("MAXPOINTS", "gifs/maxPoints.gif");

        // JFrame für die GIF-Anzeige
        gifFrame = new JFrame();
        gifFrame.setUndecorated(true);
        gifFrame.setLayout(new BorderLayout());
        gifFrame.setSize(400, 400); // Standardgröße
        gifFrame.setLocationRelativeTo(null); // Zentriert auf dem Bildschirm
    }

    public void handleSound(String topic, String JSONmsg) {
        if ("status/playSound".equals(topic) && GameConfigPanel.getInstance().getSoundCheckbox().isSelected()) {
            String sound = extractSoundFromJson(JSONmsg);
            switch (sound) {
                case "WINNER":
                    showGif("WINNER", 5500);
                    playSound("WINNER");
                    break;
                case "TREFFER":
                    playSound("TREFFER");
                    break;
                case "DOUBLE":
                    playSound("DOUBLE");
                    break;
                case "TRIPLE":
                    playSound("TRIPLE");
                    break;
                case "BULLSEYE":
                    showGif("BULLSEYE", 3000);
                    playSound("BULLSEYE");
                    break;
                case "RESET":
                    playSound("RESET");
                    break;
                case "SPIELSTART":
                    playSound("SPIELSTART");
                    break;
                case "STRAFE":
                    showGif("STRAFE", 4500);
                    playSound("STRAFE");
                    break;
                case "UEBERWORFEN":
                    showGif("UEBERWORFEN", 2700);
                    playSound("UEBERWORFEN");
                    break;
                case "MAXPOINTS":
                    showGif("MAXPOINTS", 4500);
                    playSound("MAXPOINTS");
                    break;
                default:
                    // Keine Aktion für unbekannte Sounds
                    break;
            }
        }
    }

    private void playSound(String soundKey) {
        String soundPath = soundFiles.get(soundKey);
        if (soundPath != null) {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(soundPath))) {
                Clip clip = AudioSystem.getClip();
                clip.open(audioInputStream);
                clip.start();
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Kein Sound für den Schlüssel: " + soundKey);
        }
    }

    private void showGif(String gifKey, int duration) {
        String gifPath = gifFiles.get(gifKey);
        if (gifPath != null) {
            try {
                // Erstelle und zeige das GIF
                ImageIcon gifIcon = new ImageIcon(gifPath);
                JLabel gifLabel = new JLabel(gifIcon);

                gifFrame.getContentPane().removeAll(); // Entferne alte Inhalte
                gifFrame.getContentPane().add(gifLabel, BorderLayout.CENTER);
                gifFrame.pack();
                gifFrame.setVisible(true);

                // Timer zum Ausblenden des GIFs nach der angegebenen Dauer
                Timer timer = new Timer(duration, e -> gifFrame.setVisible(false));
                timer.setRepeats(false);
                timer.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("Kein GIF für den Schlüssel: " + gifKey);
        }
    }

    private static String extractSoundFromJson(String jsonMessage) {
        try {
            // Jackson ObjectMapper zum Parsen von JSON
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonMessage);

            // Extrahiere den Wert von "sound"
            JsonNode soundNode = rootNode.get("sound");
            if (soundNode != null) {
                return soundNode.asText(); // Rückgabe als String
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Falls Parsing fehlschlägt
    }
}