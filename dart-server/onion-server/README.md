# onion-server

Dart-Server **und -Client in einem** für onlineDart — Neuimplementierung des
anthrax-servers nach Onion-Architektur. Funktioniert als **1:1-Ersatz**
(gleiche MQTT-Topics, gleiches `status/gameUpdate`-JSON nach
`dart-server/schema.json`, gleiche Sounds), kann aber mehrere Spielmodi
(**X01** mit optional Double-In/Out und **Cricket**) und läuft wahlweise als
langlebige Anwendung mit Web-Verwaltung, Lobby für Remote-Spieler,
eingebauter Punkteanzeige fürs LAN und direkter Bluetooth-Anbindung der
Dartboards (SimpleJavaBLE) — oder klassisch als One-Shot-Server per CLI.

## Schnellstart

Voraussetzung: Java 17+.

```bash
./gradlew fatJar
java -jar build/libs/onion-server-2.0-all.jar --help
```

**Web-Modus (Standard, ohne `--player`):**

```bash
java -jar build/libs/onion-server-2.0-all.jar
# Browser: http://localhost:8420/
```

Es öffnet die Web-Verwaltung: Broker starten (eingebettet oder extern),
Lobby öffnen, Spieler anlegen bzw. Remote-Clients beitreten lassen, Turnier
konfigurieren und starten — beliebig viele Turniere nacheinander, ohne
Neustart. Die Punkteanzeige für jeden Browser im LAN liegt unter
`http://<server-ip>:8420/display/` (kein Internet nötig, alles lokal
gebundelt).

**Klassischer One-Shot (wie bisher, sobald `--player` gesetzt ist):**

```bash
java -jar build/libs/onion-server-2.0-all.jar \
  --embedded-broker \
  --player Alice:1 --player Bob:2
```

Der Server startet einen eingebetteten MQTT-Broker (Moquette) auf TCP-Port
1883 und WebSocket-Port 8083 (Login `dartboard`/`smartness`), spielt genau
ein Turnier und beendet sich — exakt das bisherige Verhalten, bestehende
Aufrufe und anthrax-Configfiles laufen unverändert. Externer Broker wie
gehabt über `--mqtt-config ./mqttbroker.conf` bzw. `--mqtt-host` & Co.

## Betriebsmodi (`--mode`, Default `combined`)

| Modus      | Bedeutung |
|------------|-----------|
| `server`   | Lobby, Turnier, Web-Anzeige — keine lokalen Dartboards |
| `client`   | Dartboards per Bluetooth suchen/verbinden, Spieler in die Lobby eines entfernten Servers schicken |
| `combined` | beides: der Rechner, der den Server startet, kann auch selbst Boards verbinden und mitspielen |

```bash
# Server-Rechner (startet Broker + Verwaltung sofort, headless-tauglich):
java -jar onion-server-2.0-all.jar --mode server --auto-broker --server-name "Keller"

# Client-Rechner (verbindet sich auf den Broker des Servers):
java -jar onion-server-2.0-all.jar --mode client --web-port 8421 \
  --mqtt-host <server-ip> --mqtt-user dartboard --mqtt-password smartness --auto-broker
# Browser: http://localhost:8421/ → Board suchen → verbinden → Lobby beitreten
```

### Web-Interface (Port `--web-port`, Default 8420)

| Pfad | Zweck |
|------|-------|
| `/` | Verwaltung (Broker, Lobby, Boards, Turnier) — REST + Server-Sent-Events |
| `/display/` | Punkteanzeige wie der webapp-client, für jeden Browser im LAN; Broker-Zugang kommt automatisch aus `/config.js` |
| `/tv/` | Controller-optimierte TV-Oberfläche (Menü → Lobby → Spiel) für Wohnzimmer/SteamOS, bedienbar per Gamepad oder Pfeiltasten — siehe `dart-steamos/` |
| `/api/state` | Gesamtzustand als JSON (auch für eigene Tools) |

Die Anzeige verbindet sich per MQTT-over-WebSocket direkt auf den Broker
(Port 8083) — die Zugangsdaten stehen dafür in `/config.js` und sind damit
im LAN sichtbar (gleiches Niveau wie bisher hardcodiert im webapp-client).

### Lobby über MQTT (für eigene Clients)

| Topic | Richtung | QoS | Retained | Inhalt |
|-------|----------|-----|----------|--------|
| `lobby/state` | Server → alle | 1 | ja | `{phase, serverName, players:[{name,dartboardId,clientId,local}], gameConfig, seq}` |
| `lobby/join`  | Client → Server | 1 | nein | `{requestId, clientId, playerName, dartboardId}` |
| `lobby/leave` | Client → Server | 1 | nein | `{requestId, clientId, playerName}` — `playerName:"*"` entfernt alle Spieler des Clients (auch als MQTT Last-Will gesetzt) |
| `lobby/response/<clientId>` | Server → ein Client | 1 | nein | `{requestId, ok, error?}` |

Regeln: Spielernamen sind eindeutig; mehrere Spieler desselben Clients dürfen
sich ein Board teilen, ein Board gehört aber immer nur einem Client; Beitritt
nur bei geöffneter Lobby. Die Lobby-Aufstellung bleibt über Turniere hinweg
erhalten.

### Bluetooth-Dartboards (Client-/Combined-Modus)

Scan und Verbindung laufen direkt in Java über
[SimpleJavaBLE](https://github.com/simpleble/simpleble) (vendored unter
`libs/`, Build-Rezept und Lizenzhinweis in `libs/README.md`; Native-Library
aktuell für Linux x64 — auf anderen Plattformen bleibt der Python-Connector
`dart-board-connector/dartBlueMqttConnector` der Weg). Verbundene Boards
publishen ihre Würfe ganz normal auf `dartboard/<id>` (QoS 2) über den
Broker — Schiedsrichter-Buttons der Anzeige und Python-Connectoren mischen
sich also nahtlos. Verbindungsabrisse werden mit 5 s Backoff automatisch
neu verbunden (Boards schlafen nach Inaktivität ein; ein Tastendruck oder
Wurf weckt sie).

## Spielablauf

1. Server starten (Flags oder interaktives Setup: Spielmodus, Startpunkte,
   Anzahl Spiele, Spieler mit Dartboard-IDs, Strafkosten).
2. Das Spiel beginnt sofort; Würfe kommen als 3-stellige Codes auf
   `dartboard/<id>` herein (echtes Board via Connector oder
   `tools/virtualDartboard/virtualDartboard.html` zum Testen).
3. Nach 3 Darts wartet der Server (`WAITING`), bis der Spieler die
   NEXT-Taste (Code `999`) drückt — erst dann ist der Nächste dran.
4. Nach dem letzten Spiel druckt der Server Platzierungen und
   Turnierstatistik auf die Konsole und beendet sich.

Der Server hört nur auf das Dartboard des Spielers, der gerade am Zug ist —
mehrere Spieler können sich ein Board teilen (gleiche ID) oder eigene Boards
nutzen. Die Dartboard-ID eines Spielers muss zur `dartboard_id` in der
Connector-Konfiguration passen.

## CLI-Referenz

Jedes Flag ist optional. Ohne `--player` startet der Web-Modus; mit
`--player` der klassische One-Shot, bei dem fehlende Spielparameter
interaktiv abgefragt werden (Enter = Default). MQTT-Werte kommen aus der
Conf-Datei und werden nie abgefragt.

### Web-Modus

| Flag            | Bedeutung                                          | Default       |
|-----------------|----------------------------------------------------|---------------|
| `--mode`        | `server`, `client` oder `combined`                 | `combined`    |
| `--web-port`    | Port des Web-Interfaces                            | 8420          |
| `--server-name` | Anzeigename des Servers in der Lobby               | `Dart-Server` |
| `--auto-broker` | Broker beim Start sofort gemäß Flags starten/verbinden (sonst per Klick im Web-UI) | aus |

### MQTT (überschreibt die Conf-Datei)

| Flag               | Bedeutung                          | Default              |
|--------------------|------------------------------------|----------------------|
| `--mqtt-host`      | Hostname/IP des Brokers            | aus Conf             |
| `--mqtt-port`      | Port des Brokers                   | aus Conf / 1883      |
| `--mqtt-user`      | Benutzername                       | aus Conf             |
| `--mqtt-password`  | Passwort                           | aus Conf             |
| `--mqtt-client-id` | MQTT Client-ID                     | aus Conf / `server`  |
| `--mqtt-qos`       | QoS der `dartboard/#`-Subscription | aus Conf / 0         |
| `--mqtt-config`    | Pfad zur mqttbroker.conf           | `./mqttbroker.conf`  |

Sind `--mqtt-host`, `--mqtt-user` und `--mqtt-password` alle gesetzt, wird
keine Conf-Datei benötigt.

### Eingebetteter Broker

| Flag                        | Bedeutung | Default |
|-----------------------------|-----------|---------|
| `--embedded-broker`         | In-Process-MQTT-Broker (Moquette) starten statt einen externen zu nutzen (Schalter) | aus |
| `--embedded-broker-ws-port` | WebSocket-Port für Web-Clients (webapp, virtualDartboard) | 8083 |

Der eingebettete Broker lauscht auf allen Interfaces: TCP auf `--mqtt-port`,
WebSockets (Pfad `/`, wie Mosquitto) auf dem WS-Port. Als Login akzeptiert er
genau das Benutzer/Passwort-Paar, mit dem sich auch der Server verbindet.
Ohne weitere Angaben gelten die Repo-Defaults `dartboard`/`smartness` auf
Port 1883; eine Conf-Datei wird nur gelesen, wenn `--mqtt-config` explizit
angegeben ist. Keine Persistenz, keine Telemetrie — der Server published
nach jedem Wurf ohnehin den kompletten Spielstand.

Für den Dauerbetrieb (Broker soll Server-Neustarts überleben, mehrere
Server parallel) bleibt der eigenständige Mosquitto die Empfehlung.

### Spielmodus & Turnier

| Flag             | Bedeutung                                 | Default    |
|------------------|-------------------------------------------|------------|
| `--game-mode`    | `x01` oder `cricket`                      | `x01`      |
| `--start-score`  | Startpunkte (nur x01: 301, 501, frei)     | 301        |
| `--double-in`    | Double-In aktivieren (nur x01, Schalter)  | aus        |
| `--double-out`   | Double-Out aktivieren (nur x01, Schalter) | aus        |
| `--games`        | Anzahl Spiele des Turniers                | 1          |
| `--penalty-cost` | Kosten pro Strafpunkt in Cent             | 0          |
| `--player`       | Spieler als `name:dartboardId`, wiederholbar: `--player Alice:1 --player Bob:1` | interaktiv |

Startreihenfolge im Turnier: ab dem zweiten Spiel die umgekehrte
Platzierung des Vorspiels (der Letzte beginnt) — wie anthrax.

### Hausregeln (Default: alle an, wie anthrax)

| Flag                       | Bedeutung                             |
|----------------------------|---------------------------------------|
| `--no-schnapszahl-penalty` | Schnapszahl-Strafpunkte deaktivieren (Punktestand mit ≥2 gleichen Ziffern, z.B. 33, 111) |
| `--no-wallhit-penalty`     | Wandtreffer-Strafpunkte deaktivieren (Code `997` = sofort 1 Strafpunkt) |
| `--no-placement-penalty`   | Platzierungs-Strafpunkte deaktivieren (Platz n = n Strafpunkte, auch der Sieger zahlt 1) |

### Umsteiger von anthrax

`mqttbrokerip`→`--mqtt-host`, `mqttbrokerport`→`--mqtt-port`,
`mqttuser`→`--mqtt-user`, `mqttpassword`→`--mqtt-password`,
`mqttclientid`→`--mqtt-client-id`, `mqttqos`→`--mqtt-qos`,
`startpunkte`→`--start-score`, `spiele`→`--games`,
`kostenstrafpunkte`→`--penalty-cost`. `spieler` (nur Anzahl) entfällt —
Spieler kommen komplett über `--player` oder interaktiv.

## Spielmodi

**X01** (301/501/beliebige Startpunkte): Countdown auf exakt 0, Überwerfen =
Bust (Spielzug zählt nicht, Punktestand wie vor dem Spielzug). Optional
Double-In (erst ein Doppel öffnet das Scoring) und Double-Out (Finish nur
mit Doppel, Rest 1 = Bust).

**Cricket**: Felder 15–20 + Bull. 3 Treffer schließen ein Feld (Doppel = 2
Marks, Triple = 3, Bullseye = 2). Überzählige Marks auf ein eigenes zu, bei
mindestens einem Gegner noch offenes Feld = Punkte. Sieg: alle Felder zu und
Punkte ≥ Maximum (Gleichstand reicht). Clients bekommen die Marks im
zusätzlichen `modeData`-Feld des gameUpdate-JSON
(`marks:<Spieler>:<Feld>`, `gameMode: "CRICKET"`) — die Webapp kennt das
noch nicht, `tools/virtualDartboard` zeigt es an.

## MQTT-Schnittstelle

| Topic               | Richtung  | QoS | Inhalt |
|---------------------|-----------|-----|--------|
| `dartboard/<id>`    | eingehend | Conf | 3-stellige Wurf-Codes (siehe Root-README): erste Ziffer Multiplikator, letzte zwei Feldwert (`320` = T20); Spezial: `996` Bounce-Out/Reset, `997` Wandtreffer, `998` kein Treffer, `999` NEXT |
| `status/gameUpdate` | ausgehend | 1   | kompletter Spielstand als JSON nach `dart-server/schema.json` |
| `status/playSound`  | ausgehend | 2   | `{"sound": "TREFFER"}` usw. (TREFFER, DOUBLE, TRIPLE, BULLSEYE, WINNER, STRAFE, UEBERWORFEN, RESET, SPIELSTART, MAXPOINTS) |

Bounce-Out (`996`) mitten im Spielzug verwirft den laufenden Spielzug;
`996` nach Spielzugende (WAITING) macht den letzten Spielzug rückgängig
(idempotent — mehrfaches Drücken schadet nicht).

## Gut zu wissen

- **Bewusste Abweichungen von anthrax** (sichtbar in Clients):
  Durchschnittswerte (`avgSpiel`/`avgTurnier`) werden korrekt als
  Gleitkommazahl gerechnet (anthrax hat einen Integer-Divisions-Bug);
  `freieWuerfe` von Spielern, die nicht am Zug sind, ist immer 0.
- Der Server legt **keine Dateien** ins Arbeitsverzeichnis (kein
  `server-tcp.../.lck`-Ordner wie bei anthrax — paho läuft mit
  MemoryPersistence).
- Reconnect ist eingebaut: bei Verbindungsabriss verbindet sich der Server
  automatisch neu und resubscribed.
- QoS-Kette beachten: der Server kann eingehend nicht mehr QoS bekommen,
  als die Connectoren publishen — `mqtt_qos` konsistent halten.
- Zum Testen ohne echtes Board: `tools/virtualDartboard/virtualDartboard.html`
  im Browser öffnen (braucht den WebSocket-Port des Brokers, Default 8083).

## Entwicklung

```bash
./gradlew build          # kompilieren + alle Tests
./gradlew test           # nur Tests (Domäne, Application, Infrastruktur)
./gradlew run --args="--help"
./gradlew fatJar         # build/libs/onion-server-2.0-all.jar
```

### Architektur (Onion, 3 Ringe — Abhängigkeiten zeigen nur nach innen)
## Ziel-Architektur (Onion, 3 Ringe)
```
Infrastructure  MqttAdapter (paho) · EmbeddedBroker (Moquette) · SegmentCodec
                AnthraxJsonMapper (schema.json) · ServerSetup/ServerConfig · Main
Application     GameEngine (State-Machine RUNNING/WAITING/FINISHED)
                TournamentEngine · HouseRules · PlayerStatistics · Ports
Domain          X01Game · CricketGame (beide: DartLikeGame) · Scoreboards
                Segment · Throw · DartSet · GameEvent · GameSnapshot
```
```
┌─────────────────────────────────────────────────────────────┐
│ Infrastructure (außen)                                      │
│  MqttAdapter (paho) · SegmentCodec ("320"→T20, 996–999)     │
│  AnthraxJsonMapper (schema.json-kompatibel) · ConfigReader  │
│  CLI/stdin-Setup (commons-cli, gleiche Flags wie anthrax)   │
│  SoundMapper (GameEvent → status/playSound) · Main          │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Application                                             │ │
│ │  GameEngine (Input-Queue, State-Machine, Events)        │ │
│ │  TournamentEngine (N Spiele, Startspieler-Rotation)     │ │
│ │  HouseRules (Schnapszahl, Wandtreffer, Platzierung)     │ │
│ │  Statistics · Ports (Interfaces nach außen)             │ │
│ │ ┌─────────────────────────────────────────────────────┐ │ │
│ │ │ Domain (innen, kein Framework, keine IO)            │ │ │
│ │ │  Player · Segment · Throw · DartSet                 │ │ │
│ │ │  DartLikeGame ← X01Game(start, doubleIn/Out)        │ │ │
│ │ │              ← CricketGame                          │ │ │
│ │ │  CountingScoreboard · CricketScoreboard             │ │ │
│ │ │  GameEvent (BUST, WINNER, TRIPLE, PENALTY, 180 …)   │ │ │
│ │ └─────────────────────────────────────────────────────┘ │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

Die Spiellogik (Domain) kennt weder MQTT noch JSON: sie emittiert
`GameEvent`s und liefert einen neutralen `GameSnapshot`; die
Application-Schicht setzt Hausregeln/Statistik um und published über Ports;
die Infrastruktur übersetzt Protokoll-Codes, JSON und MQTT. Neue Spielmodi =
eine weitere `DartLikeGame`-Implementierung plus ein Eintrag in
`ServerConfig.gameFactory()`.
