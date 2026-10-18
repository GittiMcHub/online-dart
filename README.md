# onlineDart
Eine alte Hobby-Dart Gruppe aus den 90gern, hat sich wieder zusammengefunden. Dabei wurden die alten Smartness[^4] Dartschieben aus dem Keller geholt. Die meisten alten Scheiben sind leider nur für bis zu 4 Spieler ausgelegt. Durch den Corona Lockdown entstand die Idee verteilt online Dart gegeneinander zu spielen.  
Mit der Zeit entstand dieses Repository. Es ist sicherlich nicht optimal programmiert, die Diagramme sind nicht unbedingt UML-Konform - es handelt sich schlicht um ein Hobbyprojekt ;)  
Bei unserer Dartrunde haben wir verschiedene Regeln: Wandtreffer, Schnapszahlen, Falschwürfe (Werfen obwohl nicht am Zug) kosten Strafpunkte in Form von Moneten. Auch die Platzierung staffelt den Preis. Diese Regeln sollen möglichst variable eingestellt werden können. Wer ein bisschen programmieren kann, kann diesen Code sicherlich für sich anpassen.  
Anfänglich wurde eine alte E-Dart-Scheibe mit einem Raspberry Pi versehen, später haben wir dann die Smartboard Dartscheibe von Smartness für uns entdeckt und bedeuted weniger Aufwand.  

## Regelüberblick
* Punkte von 301 (Standard; der onion-server kann auch andere Startwerte, Double In/Out und Cricket)
* Bei Spielende werden Strafpunkte je nach Platzierung addiert: Platzierung=Anzahl Strafpunkte.
* Wandtreffer addieren 1 Strafpunkt
* Schnapszahlen bei Ende eines Spielzuges addieren 1 Strafpunkt. Bereits bezahlte Schnapszahlen kosten im Spiel nicht doppelt (z.B. nach Überwerfen oder 3x nicht treffen)  

## So funktioniert's
Smarte Dartscheiben (Bluetooth LE) schicken ihre Treffer über einen Connector an einen MQTT-Broker. Ein Server rechnet daraus den Spielstand und veröffentlicht ihn, Clients zeigen ihn an. Durch diese entkoppelte Architektur lässt sich sowohl mit gekauften als auch mit [selbstgebauten Smartboards](tools/buildOwnSmartboard) gegeneinander spielen.

```
Dartscheibe (BLE) → Connector → MQTT dartboard/[id] → Server → status/gameUpdate → Clients
```

Standard ist der **[onion-server](dart-server/onion-server/README.md)** mit Web-Verwaltung, TV-Oberfläche, Lobby und Computergegnern. Für Steam Deck / SteamOS gibt es ihn als App: **[dart-steamos](dart-steamos/README.md)**.

## Dokumentation
| Thema | Dokument |
|---|---|
| Alle Komponenten im Überblick (mit Screenshots) | [doc/komponenten.md](doc/komponenten.md) |
| Installation: Broker, Connectoren, Clients, Anthrax | [doc/installation.md](doc/installation.md) |
| onion-server: Start, Modi, CLI, Spielmodi, Architektur | [dart-server/onion-server/README.md](dart-server/onion-server/README.md) |
| Bots: Spielstärke, Wurfmodell, Taktik | [doc/bot.md](doc/bot.md) |
| MQTT-Topics, Sounds, Wurf-Codes, Board-Übersetzungstabelle | [doc/protokoll.md](doc/protokoll.md) |
| JSON-Schema für `status/gameUpdate` | [dart-server/schema.json](dart-server/schema.json) |
| Reverse Engineering der Bluetooth-Dartscheibe | [doc/reverse-engineering.md](doc/reverse-engineering.md) |
| Builds, Versionen & Branches (CI, Releases) | [doc/builds-releases.md](doc/builds-releases.md) |

# Copyrights und Links
Strafe sound:  https://pixabay.com "Cash Register" from MAXOU-YT  
180 sound:  https://www.myinstants.com/de/instant/180-russ-bray-48439/  
Alle anderen Sounds sind original Sounds von einem alten Smartness Dartboard, die mit einem Iphone aufgezeichnet wurden.  
Animationen Web Client:  
https://www.youtube.com/watch?v=emWCSpEDx8Y  
https://www.youtube.com/watch?v=7HrmD_vIMIk  
https://ezgif.com/video-to-gif  
https://giphy.com/gifs/VNOMtechniekenbouw-darts-raak-vnom-cNezWvIqgCYCevvLHi  
https://giphy.com/gifs/bbcamerica-funny-reaction-1lBIIjVOnY5YfdOnQe  
https://giphy.com/gifs/funny-baby-winner-LFiOdYoOlEKac

[^4]: Die Marke Smartness gehört der New Games & Sports (NG & S) GmbH
