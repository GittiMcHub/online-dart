# Onion-Server: Architektur-Vorschlag & TODO

Ziel: 1:1-Ersatz für den anthrax-server (gleiche MQTT-Topics, gleiches `schema.json`-JSON,
gleiche CLI/Config), aber mit austauschbaren Spielmodi (301, 501, X01 generisch, Cricket).

## Ist-Zustand (Analyse)

Vorhanden ist nur der innerste Ring (Domänenmodell) plus Tests:
`Segment`, `Throw`, `DartSet`, `CountingScoreboard`, `Game301`, `DartLikeGame`-Interface.
`GameEngine`, `TournamentEngine`, `Tournament` sind leere Hüllen, `Main` ist Scratch-Code.
Es fehlen komplett: MQTT, Protokoll-Übersetzung (3-stellige Codes), Config/CLI,
Statistik, Strafpunkte-Regeln, Sounds, JSON-Publishing.

Bekannte Bugs im vorhandenen Code:

- `Game301.getCurrentPlayer()` gibt immer `null` zurück (Game301.java:121)
- `CountingScoreboard.isGameOver()`: eigener TODO-Kommentar, >2-Spieler-Zweig
  hardcodet `s > 0` statt `targetScore` (bricht ADD_UP) (CountingScoreboard.java:112-119)
- `CountingScoreboard.addScore()` subtrahiert statt addiert; leerer if-Block darunter
  (CountingScoreboard.java:98-104)
- `commit()` wirft `GameOverException` bevor `clearCommit()` läuft → Scoreboard bleibt
  in altem Zustand hängen

## Ziel-Architektur (Onion, 3 Ringe)

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

Abhängigkeiten zeigen ausschließlich nach innen. Die Application-Schicht definiert Ports
(Interfaces), die Infrastruktur implementiert sie:

- `DartboardInputPort` (eingehend): liefert `Throw(Segment)` oder `Command(NEXT, BOUNCE_OUT)`
  inkl. Dartboard-ID
- `GameUpdatePublisherPort` (ausgehend): bekommt einen mode-agnostischen `GameSnapshot`
- `SoundPublisherPort` (ausgehend): bekommt `GameEvent`s

### Zentrale Design-Entscheidungen

1. **Input als Datenmodell, nicht als String.** Die 3-stelligen Protokoll-Codes
   (`101`–`320`, `225`, `996`–`999`) werden nur im `SegmentCodec` (Infrastruktur)
   übersetzt. 996/999 sind Steuer-Kommandos, keine Würfe → eigener Typ
   `DartboardInput = ThrowInput | CommandInput`. 997/998 sind Würfe mit 0 Punkten
   (`WALL_HIT`, `BOARD_HIT` existieren schon im `Segment`-Enum).

2. **Event-getrieben statt blockierender Spiel-Loop.** Anthrax blockiert Threads mit
   wait/notify (`SharedData`). Onion: MQTT-Callback schreibt in eine
   `BlockingQueue<DartboardInput>`, die `GameEngine` konsumiert sie in einem
   Engine-Thread und treibt eine State-Machine
   (`RUNNING → WAITING → RUNNING … → FINISHED`, Werte identisch zu anthrax
   `GameState` wegen Schema-Kompatibilität). Filterung nach Dartboard-ID des
   aktuellen Spielers passiert in der Engine, nicht im MQTT-Handler.

3. **Sounds als Domain-Events.** Die Spiellogik ruft nie MQTT auf, sie emittiert
   `GameEvent`s (TREFFER, DOUBLE, TRIPLE, BULLSEYE, WINNER, STRAFE, UEBERWORFEN,
   RESET, SPIELSTART, MAXPOINTS). Der `SoundMapper` außen macht daraus
   `{"sound": "..."}` auf `status/playSound` (QoS 2).

4. **301/501 sind ein Modus.** `Game301` wird zu `X01Game(int startScore,
   boolean doubleIn, boolean doubleOut)` — 301, 501 und „frei wählbare Startpunkte"
   (wie anthrax) sind Konfiguration, kein eigener Code. Damit ist auch die
   README-Anforderung double in/out abgedeckt.

5. **Cricket als zweite `DartLikeGame`-Implementierung.** Eigener
   `CricketScoreboard`: Marks-Map (15–20 + Bull, 3 Marks = zu), Punkte auf offene
   Felder, Sieg = alle Felder zu + höchste/gleiche Punktzahl. Beweist, dass das
   Interface trägt; was das Interface dafür zusätzlich braucht (z. B.
   `getSnapshot()`, `isOver()`), wird dabei sichtbar.

6. **Kompatibilität über Mapper, nicht im Domain-Modell.** `schema.json` ist
   X01-spezifisch (`punktestand`, `letzterWurf`, deutsche Feldnamen). Die Domäne
   liefert einen neutralen `GameSnapshot`; der `AnthraxJsonMapper` erzeugt daraus
   byte-kompatibles JSON für `status/gameUpdate` (QoS 1). Für Cricket wird das
   Schema erweitert (zusätzliches `modeData`-Objekt, `punktestand` = Cricket-Punkte)
   — Clients brauchen dafür ohnehin neue Anzeige.

7. **Strafpunkte/Statistik als Application-Regeln.** Schnapszahl-, Wandtreffer- und
   Platzierungs-Strafen sind Hausregeln über allen Modi → Regel-Kette in der
   Application-Schicht (konfigurierbar an/aus), nicht in den Spielmodi.
   `Statistics` beobachtet dieselben Events (Feldstatistik im schema.json-Format).

## TODO (Reihenfolge = Abhängigkeit)

### Phase 1 — Domäne reparieren und verallgemeinern ✅
- [x] Bug: `CountingScoreboard.addScore()` addiert falsch (subtrahiert) — fixen + Test
- [x] Bug: `CountingScoreboard.isGameOver()` — `targetScore` statt hardcoded `0`,
      >2-Spieler-Logik korrigieren (siehe eigener TODO-Kommentar) + Tests
- [x] Bug: `commit()` Zustand aufräumen bevor `GameOverException` fliegt
- [x] Bug: `Game301.getCurrentPlayer()` gibt `null` zurück
- [x] `Game301` → `X01Game(startScore, doubleIn, doubleOut)` umbauen;
      301/501-Parametrisierung testen, double in/out implementieren + testen
      (Paket `game.default301` → `game.x01`; `Segment` hat jetzt `multiplier`/`isDouble()`)
- [x] `DartLikeGame` erweitern: `isOver()`, `getLastTurnEvents()`;
      `getCurrentPlayer()` korrekt für alle Implementierungen
      (`getSnapshot()` bewusst auf Phase 2 verschoben — Zuschnitt entsteht mit Cricket)
- [x] `GameEvent`-Enum + Emission in `X01Game` (GAME_STARTED, THROW, BUST,
      PLAYER_FINISHED, MAX_POINTS, GAME_OVER)
- [x] Scratch-`Main` geleert (wird in Phase 4 Composition-Root)
- [x] Build repariert: Gradle-Wrapper 7.5.1 → 8.10.2, Lombok 1.18.26 → 1.18.34
      (7.5.1/1.18.26 laufen nicht unter Java 21)

### Phase 2 — Cricket ✅
- [x] `CricketScoreboard`: Marks-Verwaltung (15–20 + Bull, 3 Marks = zu,
      Bullseye = 2 Marks), Punkte nur auf Overflow-Marks solange mind. ein Gegner
      offen ist, Sieg = alles zu + Punkte >= Maximum, Ranking Sieger zuerst,
      Rest nach Punkten/Marks + Tests
- [x] `CricketGame implements DartLikeGame` + Tests (inkl. Randfälle:
      Wurf auf totes Feld, Punktgleichheit = Sieg, Wand-/Randtreffer,
      Spielende mitten im Spielzug, alles-zu-aber-zu-wenig-Punkte)
- [x] `GameSnapshot` (Record in `it.tobaben.dart.game`): gameMode, playerOrder,
      currentPlayer, scores, ranking, over, throwsLeftInTurn, lastThrowScore,
      turnScore, modeData (Cricket: `marks:<Spieler>:<Feld>`; X01: startScore,
      doubleIn, doubleOut) — `getSnapshot()` jetzt Teil von `DartLikeGame`,
      von beiden Modi implementiert + Tests

### Phase 3 — Application-Schicht ✅ (Paket `it.tobaben.dart.application`)
- [x] `DartboardInput`-Modell (THROW mit Segment vs. Command NEXT/BOUNCE_OUT,
      Dartboard-ID); 997/998 sind THROWs mit `WALL_HIT`/`BOARD_HIT`
- [x] `GameEngine`: synchrones `handle(DartboardInput)` (Queue-Konsum macht der
      TournamentEngine), State-Machine RUNNING/WAITING/FINISHED wie anthrax,
      Dartboard-ID-Filter nur während RUNNING, Bounce-Out mid-turn = Spielzug
      verwerfen, Bounce-Out in WAITING = letzten Spielzug rückgängig (idempotent),
      NEXT in WAITING/FINISHED von jedem Board, Sounds + Publishing wie anthrax
- [x] Domäne dafür erweitert: `endTurn()` (NEXT mit Restdarts), `abortTurn()`
      (Bounce-Out), `undoLastTurn()` (Bounce-Out nach Spielzugende) in
      `DartLikeGame`, beide Modi; `GameEvent.TURN_ENDED`
- [x] `TournamentEngine`: N Spiele über eine BlockingQueue, Startreihenfolge =
      umgekehrte Platzierung des Vorspiels (wie anthrax Main), Spieldaten-Reset
      zwischen Spielen, Turnierstatistik akkumuliert; die leere `Tournament`-Klasse
      wurde ersatzlos gestrichen (TournamentEngine übernimmt)
- [x] `HouseRules` (Record, einzeln abschaltbar): Schnapszahl (nur wenn sich der
      Punktestand geändert hat → nicht doppelt zahlen), Wandtreffer-Strafpunkt,
      Platzierungs-Strafpunkte (Platz n = n Punkte, auch der Sieger — wie anthrax)
- [x] `PlayerStatistics` mit allen anthrax-Feldern inkl. `highestFinish` und
      `getroffeneFelder[3][21]` (Feldnamen identisch für den JSON-Mapper);
      Semantik wie anthrax: Fehlwürfe/Wandtreffer zählen als Würfe, Bust-Darts
      zählen in die Summen, Reset zwischen Spielen nur Spiel-Zähler.
      Bewusste Abweichung: avg ohne anthrax' Integer-Divisions-Bug
- [x] Ports: `GameUpdatePublisherPort` (bekommt `EngineUpdate` = GameSnapshot +
      Engine-Kontext), `SoundPublisherPort` (bekommt `Sound`-Enum, Namen = anthrax)

### Phase 4 — Infrastruktur (Anthrax-Parität) ✅ (Paket `it.tobaben.dart.infrastructure`)
- [x] `SegmentCodec`: "101"–"320"/"225" → `Segment`, "996"–"999" → Commands,
      Validierung (nur 3 Ziffern, unbekannte Codes verworfen) + Tests gegen
      Übersetzungstabelle im Root-README (alle 60 Felder + Bull/Bullseye)
- [x] `MqttAdapter` (paho 1.2.5): subscribe `dartboard/#` (QoS aus Config),
      publish `status/gameUpdate` QoS 1 + `status/playSound` QoS 2;
      Reconnect über paho `setAutomaticReconnect` + Resubscribe in
      `connectComplete` (robuster als anthrax' manueller Retry-Thread)
- [x] `MqttConfig` statt ConfigReader: `mqttbroker.conf` im anthrax-Format
      (Properties, `mqtt_broker_ip` usw.) — bestehende Configfiles laufen
      unverändert; Beispiel-Conf liegt im Projektordner
- [x] CLI: eigene, zur Onion-Implementierung passende Flags (bewusst NICHT die
      anthrax-Namen — der Server hat mehr zu konfigurieren als anthrax):

      MQTT (überschreibt mqttbroker.conf; ohne Flags gilt die Conf-Datei):
      | Flag               | Bedeutung                          | Default            |
      |--------------------|------------------------------------|--------------------|
      | `--mqtt-host`      | Hostname/IP des Brokers            | aus Conf           |
      | `--mqtt-port`      | Port des Brokers                   | aus Conf / 1883    |
      | `--mqtt-user`      | Benutzername                       | aus Conf           |
      | `--mqtt-password`  | Passwort                           | aus Conf           |
      | `--mqtt-client-id` | MQTT Client-ID                     | aus Conf / generiert |
      | `--mqtt-qos`       | QoS für dartboard/#-Subscription   | aus Conf / 0       |
      | `--mqtt-config`    | Pfad zur mqttbroker.conf           | ./mqttbroker.conf  |

      Eingebetteter Broker (Moquette, statt externem Mosquitto):
      | Flag                        | Bedeutung                                        | Default |
      |-----------------------------|--------------------------------------------------|---------|
      | `--embedded-broker`         | In-Process-Broker starten (Schalter); TCP auf `--mqtt-port`, Login = `--mqtt-user`/`--mqtt-password`; ohne weitere Angaben 127.0.0.1:1883, dartboard/smartness (Conf-Datei wird nur bei explizitem `--mqtt-config` gelesen) | aus |
      | `--embedded-broker-ws-port` | WebSocket-Port für Web-Clients (webapp, virtualDartboard) | 8083 |

      Spielmodus & Turnier:
      | Flag               | Bedeutung                                   | Default |
      |--------------------|---------------------------------------------|---------|
      | `--game-mode`      | `x01` oder `cricket`                        | `x01`   |
      | `--start-score`    | Startpunkte (nur x01: 301, 501, frei)       | 301     |
      | `--double-in`      | Double-In aktivieren (nur x01, Schalter)    | aus     |
      | `--double-out`     | Double-Out aktivieren (nur x01, Schalter)   | aus     |
      | `--games`          | Anzahl Spiele des Turniers                  | 1       |
      | `--penalty-cost`   | Kosten pro Strafpunkt in Cent               | 0       |
      | `--player`         | Spieler als `name:dartboardId`, wiederholbar (`--player Alice:1 --player Bob:1`) | interaktiv |

      Hausregeln (Schalter, Default = alle an wie anthrax):
      | Flag                        | Bedeutung                                  |
      |-----------------------------|--------------------------------------------|
      | `--no-schnapszahl-penalty`  | Schnapszahl-Strafpunkte deaktivieren       |
      | `--no-wallhit-penalty`      | Wandtreffer-Strafpunkte deaktivieren       |
      | `--no-placement-penalty`    | Platzierungs-Strafpunkte deaktivieren      |

      Mapping alt → neu (für Umsteiger dokumentieren): `mqttbrokerip`→`--mqtt-host`,
      `mqttbrokerport`→`--mqtt-port`, `mqttuser`→`--mqtt-user`,
      `mqttpassword`→`--mqtt-password`, `mqttclientid`→`--mqtt-client-id`,
      `mqttqos`→`--mqtt-qos`, `startpunkte`→`--start-score`, `spiele`→`--games`,
      `kostenstrafpunkte`→`--penalty-cost`; `spieler` (nur Anzahl) entfällt —
      Spieler kommen komplett über `--player` oder interaktiv.

      Interaktives stdin-Setup wie anthrax als Fallback: alles, was nicht per
      Flag gesetzt ist (Spielmodus, Startpunkte, Spiele, Spieler+Board-IDs,
      Strafkosten), wird nacheinander abgefragt (Enter = Default).
      `--help` zeigt alle Flags. Umsetzung: `ServerSetup` (commons-cli,
      testbar über injizierte In-/Out-Streams) → `ServerConfig`-Record.
- [x] `AnthraxJsonMapper`: `EngineUpdate` → schema.json-JSON (deutsche Feldnamen,
      `gameState`-Strings identisch, `statistik` 1:1 aus `PlayerStatistics`);
      Struktur-Tests gegen alle schema.json-Pflichtfelder. X01 ohne Zusatzfelder;
      Cricket hängt `gameMode` + `modeData` an, `punktestand` = Cricket-Punkte.
      Echte Golden-File-Diffs gegen anthrax → Phase 5 Paritätstest
- [x] Sound-JSON `{"sound": "..."}` (Tabelle im Root-README) als
      `MqttAdapter.soundJson(Sound)` + Test — eigener SoundMapper unnötig,
      Engine mappt Events schon auf das `Sound`-Enum
- [x] `Main` als Composition-Root: `ServerSetup` → `MqttAdapter` →
      `TournamentEngine`; druckt am Ende Platzierungen + Turnierstatistik
- [x] build.gradle (aus Phase 5 vorgezogen): paho/gson/commons-cli,
      `application`-Plugin (`./gradlew run`), `fatJar`-Task →
      `build/libs/onion-server-1.0-SNAPSHOT-all.jar` (getestet mit `--help`)
- [x] `EmbeddedBroker` (Moquette 0.17, `--embedded-broker`): In-Process-MQTT-Broker,
      Konfiguration komplett im Code (TCP 0.0.0.0:`--mqtt-port`, WebSocket
      `--embedded-broker-ws-port` mit Pfad `/` wie Mosquitto, Auth = genau das
      Benutzer/Passwort-Paar der MqttConfig, keine Persistenz, Telemetrie aus).
      Integrationstest mit echten paho-Clients (TCP + WebSocket + falsches
      Passwort); End-to-End-Smoke-Test: komplettes 301-Spiel über den
      eingebetteten Broker gespielt
- [x] `MqttAdapter` nutzt `MemoryPersistence` statt paho-Default-Filepersistenz —
      kein `server-tcp.../.lck`-Ordner mehr im Arbeitsverzeichnis (Server
      published eh nach jedem Wurf den kompletten Spielstand)

### Phase 5 — Abnahme & Rollout
- [ ] Paritätstest: gleiches Wurf-Skript gegen anthrax und onion spielen,
      `status/gameUpdate`-Nachrichten diffen (Webapp + java-ui-client müssen
      unverändert funktionieren); Achtung erwartete Diffs: avg korrekt statt
      Integer-Division, `freieWuerfe` der Nicht-am-Zug-Spieler immer 0
- [ ] `schema.json` für Cricket erweitern (`modeData`, `gameMode`),
      Client-Impact dokumentieren
- [ ] Dockerfile analog anthrax; Build-/Start-Anleitung in README