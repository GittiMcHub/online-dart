# Bot-Gegner (Bot-Kern) — Design

Datum: 2026-10-16 · Komponente: `dart-server/onion-server` (+ Web-UIs `management/`, `tv/`, `dart-server/schema.json`)

## Ziel

Ein computergesteuerter Spieler („Bot“), gegen den man spielen kann:

- **Solo-Training** gegen einen Bot mit wählbarer Spielstärke.
- **Team gegen Bot** (z. B. Cricket: zwei Menschen teilen sich einen Spieler, der Bot ist der Gegner).
- Grundlage für spätere Teilprojekte (siehe „Folgeprojekte“).

Spielstärke ist **ein** Wert: das 3-Dart-Scoring-Average **Ø 0–180** (0 = trifft nie, 180 = immer
3× T20). Presets Easy/Normal/Hard/Pro + frei wählbar.

## Entscheidungen

| Thema | Entscheidung |
|---|---|
| Ort | Im onion-server, **nur auf dem Host** (Modi `server`/`combined`, damit auch in der SteamOS-AppImage). Kein Client-seitiger Bot, kein neues Lobby-MQTT-Topic. |
| Wer legt Bots an | Nur der Host (Management-Web-UI und `/tv/`). |
| Eingabepfad | Bot = **virtuelles Dartboard**. Er publiziert Wire-Codes (`320`, `998`, `999` …) über den vorhandenen `ThrowPublisherPort` auf `dartboard/<id>`. `GameEngine`, `X01Game`, `CricketGame`, `SegmentCodec`, MQTT-Eingang bleiben unverändert. |
| Board-IDs | **900–999 sind für Bots reserviert**; der Host vergibt sie. Lobby-Join und BLE-Zuordnung lehnen diese IDs für Menschen ab. |
| Hausregeln/Geld | Bot ist **vollwertiger Spieler**: Strafpunkte (Schnapszahl, Platzierung), Strafgeld, Statistik — gleicher Codepfad wie Menschen. |
| Kennzeichnung | Optionales Feld `"bot": true` pro Spieler im gameUpdate-JSON (`schema.json`, rückwärtskompatibel). Anzeige z. B. „Bot (Ø 40)“. |
| Spielstärke-UI | Presets **Easy Ø 25 · Normal Ø 40 · Hard Ø 60 · Pro Ø 85** + „Custom“-Slider 0–180 in 5er-Schritten (controllertauglich). |
| Strategie-Intelligenz | **An das Average gekoppelt**, kein eigener Regler: Ø ≥ 40 = „smart“ (Checkout-Tabelle, Schnapszahl-/Bogey-Vermeidung), Ø < 40 = „naiv“. |
| Legacy | `LegacyRun` (`--player`) unterstützt keine Bots. |

## Architektur

```
TournamentConfig + Bot-Lineup ──► BotService (application/bot)
                                     │  hört EngineUpdates (Decorator um GameUpdatePublisherPort)
                                     │  wählt Ziel (BotStrategy) → würfelt Segment (ThrowModel)
                                     ▼
                       ThrowPublisherPort → MQTT dartboard/<9xx> → SegmentCodec → inputQueue → GameEngine
```

### Domain `it.tobaben.dart.game.bot` (rein, keine Threads/MQTT, `Random` injizierbar)

- **`BoardGeometry`** — Punkt (x, y) in mm → `Segment`. Standard-Proportionen als benannte
  Konstanten: Bullseye r 6,35 · Bull r 15,9 · Triple 99–107 · Double 162–170 · Sektorreihenfolge
  20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5 (20 oben, je 18°).
  r > 170 → `BOARD_HIT` (Miss, Wire `998`). Keine `WALL_HIT`s, kein Bounce-Out (`996`/`997` erzeugt
  der Bot nie). Absolute Maße sind egal (Soft-Tip vs. Steel), da die Kalibrierung Ø → σ in
  denselben Einheiten abbildet.
- **`ThrowModel`** — Zielpunkt + σ → isotrope 2D-Normalverteilung → Punkt → `Segment`. σ = 0 trifft
  exakt. Liefert außerdem pro Zielpunkt die (gecachte) Segment-Verteilung für die Strategie.
- **`DifficultyCalibration`** — Ø ↔ σ als **konstante Tabelle im Code** (~40 Paare σ → Ø, einmalig
  per Monte-Carlo erzeugt: T20 gezielt, fester Seed), invertiert per linearer Interpolation. Die
  Abbildung hängt nur von Geometrie und Zielpunkt ab, ist also fest — keine Berechnung zur
  Laufzeit. Nachgeschlagen wird nur beim Anlegen des Bots; jeder Dart wird danach von der
  Strategie aus dem Spielzustand entschieden und mit diesem σ gestreut. Ein Test misst die
  Tabelle per Monte-Carlo nach (siehe Tests). Die Kurve fällt monoton; Orientierung aus dem
  Brainstorming-Prototyp:

  | σ (mm) | 0 | 5 | 10 | 15 | 20 | 25 | 30 | 50 | 80 | 130 | 250 |
  |---|---|---|---|---|---|---|---|---|---|---|---|
  | Ø | 180 | 129 | 88 | 65 | 52 | 45 | 41 | 33 | 26 | 18 | 7 |

  Presets ergeben damit ca. σ 85 (Easy, ~⅓ der Darts neben dem Board), 31 (Normal), 16 (Hard),
  10 (Pro). Ø bedeutet **Scoring-Stärke**; das in der Statistik angezeigte Spiel-Average liegt
  wegen Checkout-Darts und Busts etwas darunter — gewollt, wird nicht kompensiert.
- **`BotProfile`** — `record BotProfile(int average)` mit abgeleitetem σ und `smart = average >= 40`.
- **`BotStrategy`** (Interface): `(GameSnapshot, Player self, int dartsLeft, HouseRules) → Zielpunkt`.
  Implementierungen `X01Strategy`, `CricketStrategy`. Spielzustand kommt ausschließlich aus
  `GameSnapshot` (`scores`, `modeData`: `startScore`, `doubleIn`, `doubleOut`,
  `marks:<player>:<number>`).

### Application `it.tobaben.dart.application.bot`

- **`BotService`** — erhält beim Turnierstart die Bots (Board-ID → `BotProfile`) und die
  `TournamentConfig`. Wird als **Decorator um `GameUpdatePublisherPort`** in `ServerSession`
  verdrahtet (leitet jedes `EngineUpdate` unverändert weiter und wertet es aus). Eigener
  `ScheduledExecutorService`:
  - `currentPlayer` ist Bot und `state == RUNNING` → nach Verzögerung einen Dart wählen und
    Wire-Code publizieren.
  - `currentPlayer` ist Bot und `state == WAITING` → nach Verzögerung `999` publizieren.
- **`BotTiming`** — `record BotTiming(Duration minDart, Duration maxDart, Duration nextDelay, Duration watchdog)`,
  Standard 1,5–3,0 s gleichverteilt zwischen Darts, 2,5 s vor `999`, Watchdog 10 s. Tests: alles 0.

### Lobby / Modell

- `LobbyPlayer` bekommt optional ein `BotProfile` (null = Mensch); Bots haben
  `clientId = LOCAL_CLIENT_ID`. Neue Host-Operation `ServerSession.addBot(name, average)` vergibt
  die nächste freie ID ≥ 900. Entfernen/Umsortieren wie lokale Spieler.
- `RegisteredPlayer` bekommt optional `BotProfile` (Konstruktor-Overload, bestehende Aufrufe bleiben).
- `joinLobby` mit Board-ID 900–999 → `LobbyResult.failure("Board-IDs 900–999 sind für Bots reserviert")`.
- REST: neuer Endpoint im `ApiHandler` zum Anlegen eines Bots (`name`, `average`); Lobby-State-JSON
  enthält pro Spieler `bot`/`average`.
- `AnthraxJsonMapper`/`StateJsonMapper`: `"bot": true` nur für Bots ausgeben; `schema.json` ergänzen.
- UI: Management und `/tv/` erhalten „Bot hinzufügen“ (Preset-Auswahl + Custom-Slider);
  Standardname „Bot (Ø 40)“, editierbar. Score-Anzeigen markieren Bots (Icon/Suffix).

## Strategie

### X01 — smart (Ø ≥ 40)

Pro Dart, in dieser Reihenfolge:

1. **Double-In ausstehend** (`doubleIn` und Score == `startScore`) → D20.
2. **Checkout mit den verbleibenden Darts möglich** → erster Dart der Route aus der
   Checkout-Tabelle (Ressource `bot/checkouts.txt`, Standardrouten 170…2, bevorzugte Doubles
   D16/D20/D8). Ohne `doubleOut`: direkt auf das exakte Segment zielen. Die Tabelle ist reiner
   Bot-Code und unabhängig von der Finish-Hilfe der TV-UI (`web/tv/js/checkout.js`); dass der Bot
   eine andere Route wählt als der angezeigte Hinweis, ist in Ordnung.
3. **Setup-Bereich** (Rest ≤ ~100, kein Checkout) → Dart wählen, der ein Wunsch-Double übrig lässt
   (40, 32, 36, 24, 16 …), Singles bevorzugt.
4. **Sonst** → T20.

**Schnapszahl-Filter** (nur wenn Hausregel aktiv und dieser Dart den Zug beenden kann, d. h.
letzter Dart oder möglicher Finish): Kandidaten in Reihenfolge (Regel-Ziel, T20, T19, S20, S19,
Setup-Alternativen); erster Kandidat mit **P(Rest ist Schnapszahl) < 15 %** gemäß
Segment-Verteilung des Bot-σ. Hintergrund: die Engine wertet die Schnapszahl auf den Rest am
Zugende, nur wenn sich der Score geändert hat (`GameEngine.finishTurn`). **Bogey-Zahlen**
(169, 168, 166, 165, 163, 162, 159) werden genauso, mit niedrigerem Gewicht, gemieden.

### X01 — naiv (Ø < 40)

T20 bis Rest ≤ 60. Mit `doubleOut`: ungerader Rest → Single so wählen, dass ein gerader Rest ≤ 40
bleibt, dann D(Rest/2). Ohne `doubleOut`: exaktes Segment. Kein Schnapszahl-Filter.

### Cricket

- **Naiv**: Zahlen der Reihe nach 20 → 15 → Bull, jeweils aufs Triple (Bull: Zentrum).
- **Smart**: Liegt der Bot nach Punkten zurück und hat er eine geschlossene Zahl, die ein Gegner
  offen hat → auf die höchste solche punkten. Sonst die höchste eigene offene Zahl schließen,
  bevorzugt Zahlen, die ein Gegner bereits geschlossen hat (Punkte des Gegners blocken).

## Fehlerbehandlung

- **Wahrheit ist immer das letzte `EngineUpdate`.** Höchstens eine geplante Aktion, Schlüssel
  `(gameId, currentPlayer.id, freieWuerfe, state)`. Jedes neue Update mit anderem Schlüssel
  storniert sie → kein Doppel-Dart nach Undo, Zug-Abbruch oder Host-Eingriff.
- **Watchdog**: Ist ein Bot am Zug und kommt 10 s kein Update, wird die Aktion zum aktuellen
  Schlüssel erneut ausgeführt (zustandsgebunden, Duplikat daher unkritisch).
- **Publish-Fehler** (Broker weg): loggen, Watchdog wiederholt.
- **Turnier-/Session-Ende**: Scheduler herunterfahren, offene Aktionen verwerfen.

## Tests (JUnit 5, deterministisch per Seed)

| Bereich | Prüft |
|---|---|
| `BoardGeometry` | Zentrum → `BULLS_EYE`, (0, 103) → `TRIPLE_20`, Sektorgrenzen (±9°), r > 170 → `BOARD_HIT` |
| `ThrowModel` | σ = 0 trifft exakt; Verteilung summiert auf 1 |
| `DifficultyCalibration` | Konstante Tabelle stimmt: für Ø 10/25/40/60/85/120 misst Monte-Carlo (30k Züge) Ø innerhalb ±2; Tabelle monoton |
| Checkout-Tabelle | Jede Route rechnet korrekt auf 0, endet auf Double/Bullseye, max. 3 Darts; Bogey-Zahlen (169, 168, 166, 165, 163, 162, 159) fehlen |
| `X01Strategy` | Tabellengetrieben (Rest, Darts, doubleIn/Out, smart) → Ziel; Schnapszahl-Fall: Rest 171 beim letzten Dart zielt nicht auf T20 (→ 111); naive Pfade |
| `CricketStrategy` | Schließen, Blocken, Punkten |
| `BotService` | Fake-Executor + Loopback-`ThrowPublisherPort` → `inputQueue` mit echtem `TournamentEngine`: zwei Bots σ = 0 spielen 301 deterministisch zu Ende; Undo während Bot-Zug erzeugt keinen Extra-Dart; Watchdog sendet nach Ausfall erneut |
| Lobby | Board-ID 900–999 für Menschen abgelehnt; `addBot` vergibt freie IDs |

## Lieferumfang / Repo-Regeln

- `version` in `dart-server/onion-server/build.gradle` erhöhen (CI-Regel).
- `dart-server/schema.json`: optionales `bot`.
- `dart-server/onion-server/README.md`: Abschnitt „Bots“.

## Nicht im Umfang (Folgeprojekte)

1. **Ghost / Replay** (eigene Spec): Aufzeichnung von Solo-X01-Spielen (rohe `DartboardInput`s pro
   Zug + Einstellungen als JSON), `ReplayThrowSource` für abwesende Turnierspieler, Fallback auf
   Bot mit aus der Aufnahme geschätztem σ; nur X01. Signaturen (Aufnehmender + Zeuge, `contentHash`
   als Manipulationsindikator, keine Kryptografie/PKI). Dafür wird der Bot-Kern um ein
   `ThrowSource`-Interface erweitert (simuliert vs. Replay).
2. **Multi-Board-Teamspieler**: ein Spieler, der Eingaben von mehreren Boards akzeptiert (Remote-Team).
3. Ideen: absichtlicher Bust zur Schnapszahl-Vermeidung, getrenntes σx/σy, „Doppel-Nerven“,
   Formschwankung, Zielen aufs Zentrum bei großem σ.
