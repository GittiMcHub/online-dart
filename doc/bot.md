# Bots (Computergegner)

Der onion-server kann Computergegner ins Turnier nehmen: zum Üben allein, als Gegner für ein
Cricket-Team oder einfach als zusätzlicher Mitspieler. Ein Bot ist ein **vollwertiger Spieler** —
Strafpunkte, Strafgeld und Statistik zählen wie bei Menschen.

## Bot hinzufügen

Nur der Host (der Rechner, auf dem der Server läuft) kann Bots hinzufügen, und zwar nur bei
geöffneter Lobby. Die Bots laufen auch auf dem Host.

* Management-UI: Formular „Bot hinzufügen"
* TV-UI (`/tv/`): „+ Bot hinzufügen…"
* REST: `POST /api/lobby/bots` mit `{"name"?: "...", "average": 0–180}`

Ohne Namen heißt der Bot „Bot (Ø 40)", weitere gleich starke „Bot 2 (Ø 40)", „Bot 3 (Ø 40)" usw.

## Spielstärke

Die Spielstärke ist das **3-Dart-Average** (Ø Punkte pro Aufnahme), wie es auch Dartspieler für sich
selbst angeben. Ø 0 trifft nie die Scheibe, Ø 180 wirft immer drei Triple 20.

| Preset | Ø | Streuung σ | Gefühl |
|---|---|---|---|
| Easy | 25 | ≈ 85 mm | trifft die Scheibe, aber kreuz und quer |
| Normal | 40 | ≈ 31 mm | meist in der Nähe des Ziels |
| Hard | 60 | ≈ 16 mm | trifft oft das Triple |
| Pro | 85 | ≈ 10 mm | verfehlt selten deutlich |

Dazwischen ist jeder Wert in 5er-Schritten wählbar (Schieberegler).

### Für Nicht-Programmierer erklärt

Der Bot **entscheidet nicht, was er trifft** — er entscheidet nur, **wohin er zielt** (z.B. Triple 20).
Dann verzieht der Zufall den Pfeil ein Stück, wie bei einer echten, leicht zittrigen Hand: Die meisten
Pfeile landen nah am Zielpunkt, einige weiter weg. Welches Feld getroffen wurde, ergibt sich aus der
Stelle, an der der Pfeil landet — genau wie bei einem Menschen. Deshalb wirft auch ein schwacher Bot
mal eine 180 und ein starker mal eine schlechte Aufnahme.

Die Schwierigkeit verändert **nur, wie stark der Pfeil verzieht**: bei Easy landen die Pfeile typisch
8–9 cm neben dem Zielpunkt (halbe Scheibe, ein Triple ist Glückssache), bei Pro etwa 1 cm (so breit
wie der Triple-Ring).

Welche Streuung zu welchem Average führt, wurde einmal vorab ausgerechnet: Für jede Streuung wurden
Millionen Würfe simuliert und das Ergebnis-Average notiert. Diese Tabelle ist fest im Programm
hinterlegt; wählt man „Ø 40", schlägt der Bot nach, welche Streuung Ø 40 ergibt.

## Wurfmodell (technisch)

* **Geometrie** (`game/bot/BoardGeometry`): Standard-Dartscheibe in mm (Bullseye 6,35, Bull 15,9,
  Triple 99–107, Double 162–170). Außerhalb des Double-Rings = Fehlwurf (998). Nur die Proportionen
  zählen — Soft- oder Steeldart-Maße ändern das Ergebnis nicht, weil σ in denselben Einheiten
  kalibriert ist.
* **Streuung** (`game/bot/ThrowModel`): Treffpunkt = Zielpunkt + isotrope 2D-Normalverteilung mit
  Standardabweichung σ in x und y. `distribution(ziel)` liefert die Trefferwahrscheinlichkeit je
  Segment (Monte Carlo, 4000 Würfe, fester Seed, gecacht) — die Taktik nutzt sie zur Risikoabschätzung.
* **Kalibrierung** (`game/bot/DifficultyCalibration`): konstante Tabelle σ → Ø, erzeugt per Monte Carlo
  (2 000 000 Pfeile pro Zeile, Ziel immer T20-Mitte). Ø fällt monoton mit σ, daher reicht lineare
  Interpolation zwischen den Zeilen; ein separater „Fehlwurf-Anteil" ist nicht nötig.
  `DifficultyCalibrationTest` misst die Tabelle nach. Die Tabelle hängt nur von Geometrie und Zielpunkt
  ab und wird deshalb **nicht** beim Start berechnet.
* **Profil** (`game/bot/BotProfile`): `average` 0–180, daraus `sigma()` und `smart()` (ab Ø 40).

Ergebnis-Hinweis: Das reale Average eines taktisch spielenden Bots weicht leicht vom eingestellten ab,
weil er beim Auschecken nicht mehr auf T20 zielt.

## Taktik

Unter Ø 40 spielt der Bot **naiv**, ab Ø 40 **taktisch**. Die Taktik bestimmt nur den Zielpunkt; ob
er trifft, entscheidet das Wurfmodell.

### X01 (`game/bot/X01Strategy`)

| Situation | naiv (< Ø 40) | taktisch (≥ Ø 40) |
|---|---|---|
| Double-In, noch nicht eröffnet | D20 | D20 |
| Rest > 60 | T20 | Route aus Checkout-Tabelle, sonst T20 |
| Double-Out im Finishbereich | direktes Double, sonst auf 40 runterspielen | Checkout-Route; ohne Route ein Setup-Wurf auf ein Lieblingsdouble (D20, D16, D18, D12, …) |
| Ohne Double-Out | exaktes Feld zum Auschecken | exaktes Feld zum Auschecken |
| Letzter Pfeil der Aufnahme | — | meidet Reste, die Strafe kosten (Schnapszahl, wenn die Hausregel an ist) oder Bogey-Zahlen (169, 168, 166, 165, 163, 162, 159) |

Den letzten Pfeil prüft der Bot so: Für das geplante Ziel und die Alternativen T20, T19, T18, S20, S19,
S18 rechnet er über die Trefferverteilung aus, wie wahrscheinlich ein Strafrest ist (Bogey zählt
halb). Das erste Ziel mit Risiko unter 15 % gewinnt, sonst das mit dem kleinsten Risiko. Absichtlich
überwerfen tut er nicht.

**Checkout-Tabelle**: `src/main/resources/bot/checkouts.txt` (onion-server), Format
`<rest> <pfeile…>`, z.B. `100 T20 D20`. Sie ist Bot-Code und unabhängig von der Finish-Anzeige im TV —
der Bot darf einen anderen Weg wählen.

### Cricket (`game/bot/CricketStrategy`)

* **naiv**: schließt stur 20 → 19 → … → 15 → Bull.
* **taktisch**, in dieser Reihenfolge:
  1. liegt er hinten: punkten auf einer eigenen geschlossenen Zahl, die ein Gegner noch offen hat;
  2. Zahlen schließen, die ein Gegner schon geschlossen hat (blocken);
  3. höchste offene Zahl schließen;
  4. alles zu, aber noch hinten: weiter punkten.

Gezielt wird auf das Triple der Zahl bzw. die Bullmitte.

## Ablauf im Spiel

Der Bot wirft über ein **virtuelles Dartboard** und benutzt damit denselben Weg wie eine echte
Scheibe — die Spiellogik (`GameEngine`) weiß nichts von Bots.

```
GameEngine ──EngineUpdate──▶ BotService ──Segment──▶ MqttVirtualBoard ──"320"──▶ MQTT dartboard/<id>
     ▲                                                                                  │
     └──────────────────────────────── DartboardInput ◀───────────────────────────────┘
```

* **Board-IDs 900–999** sind für Bots reserviert; Menschen und echte Boards können sie nicht nutzen.
* **`BotService`** (application/bot) hängt als Dekorator am `GameUpdatePublisherPort` und sieht jedes
  Spielupdate. Ist ein Bot am Zug, plant er genau eine Aktion:

  | Spielzustand | Aktion des Bots |
  |---|---|
  | RUNNING | nächster Pfeil nach 1,5–3 s (zufällig) |
  | WAITING (Aufnahme vorbei) | Pfeile ziehen (999) nach 2,5 s |
  | FINISHED (Bot hat das Spiel beendet) | weiter (999) nach 5 s |

* Jede Aktion gehört zu einem Spielzustand (Spiel, Spieler, Restpfeile, Zustand). Ändert sich der
  Zustand vorher — Undo, Bounce-Out, ein Mensch drückt NEXT —, wird die geplante Aktion verworfen.
  So entstehen keine doppelten Pfeile.
* **Watchdog**: Kommt 10 s keine Reaktion (z.B. Nachricht im Broker verloren), wird die Aktion
  erneut gesendet.
* Turnierstart startet die Bots, Turnierende/-abbruch stoppt sie.

## Anzeige & Schnittstelle

* Im `status/gameUpdate` tragen Bot-Spieler `"bot": true` (siehe `dart-server/schema.json`).
* Die Lobby (REST/SSE, `lobby/*`) liefert `bot` und `average`.
* Die TV-Lobby zeigt ein Bot-Abzeichen. Die Spielanzeigen markieren Bots bisher nicht eigens — nur der
  Standardname „Bot (Ø x)" verrät sie.

## Bekannte Grenzen

* Bots gibt es nur für X01 und Cricket.
* Hängt die Broker-Verbindung, ohne abzubrechen, kann ein Bot-Wurf den Server bis zu ~15 s blockieren.
* Seltene Millisekunden-Rennen: Drückt ein Mensch NEXT genau während das NEXT des Bots unterwegs ist,
  kann ein Solo-Bot eine Aufnahme verlieren; ein Pfeil, der beim Turnierabbruch unterwegs ist, könnte
  im nächsten Turnier zählen.
* Im Client-Modus sind `POST /api/lobby/bots` und Lobby-Beitritt per REST erreichbar (die UI blendet
  sie aus).
* Wirft die Taktik dauerhaft einen Fehler, wiederholt der Watchdog still alle 10 s.

## Ausblick

* **Geist / Replay**: eine aufgezeichnete Solo-X01-Partie eines abwesenden Spielers wird am
  Turniertag nachgespielt (Aufnahme-JSON mit Signatur des Aufzeichners und eines Zeugen) — eigene Spec.
* Team-Spieler über mehrere Boards.
