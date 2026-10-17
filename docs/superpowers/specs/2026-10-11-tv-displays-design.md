# TV-Anzeigen: Finish-Hilfe, Trefferbild, Blitztabelle — Design

Datum: 2026-10-11 · Komponente: `dart-server/onion-server` (TV-UI `web/tv/` + Engine)

## Ziel

Die TV-Oberfläche (`/tv/`) bekommt im Spielmenü einen Punkt **„Anzeigen“** mit drei
optionalen Einblendungen während des Spiels:

1. **Finish-Hilfe** (nur X01) — wie der aktuelle Spieler mit den nächsten Darts finishen kann.
2. **Trefferbild** — Heatmap der getroffenen Felder des aktiven Spielers.
3. **Blitztabelle** — Turnier-Ranking nach Strafpunkten inkl. Prognose für das laufende Spiel.

Erfolg: Alle drei sind per Controller ein-/ausschaltbar, die Wahl bleibt pro TV erhalten,
die Spielanzeige bleibt auf einem 1080p-TV ohne Scrollen lesbar.

## Entscheidungen

| Thema | Entscheidung |
|---|---|
| Finish-Hilfe-Berechnung | **Client-seitig** (JS), aus gameUpdate-Daten |
| X01-Regeln im gameUpdate | Server sendet **auch für X01** `gameMode`/`modeData` (bisher nur Nicht-X01) |
| Blitztabelle-Prognose | **Server-seitig**, neue optionale Felder je Spieler |
| Blitztabelle bei 0 Cent | nur Platzierungen zählen (Platz 1 = 1, 2 = 2 …, Summe über alle Spiele, unabhängig von Hausregeln); Wand-/Schnapszahl-Strafen egal |
| Finish-Hilfe in WAITING | ausgeblendet (kein Vorschau-Hinweis für den nächsten Spieler) |
| Trefferbild-Datenbasis | **ganzes Turnier** (`getroffeneFelder` wie vorhanden) |
| Out-Regeln | nur das vorhandene Double-Out; **kein Triple-Out** |
| Position Finish-Hilfe | zwischen aktiver Kachel und Aktionsleiste |
| Position Trefferbild/Blitztabelle | Zeile unter der Aktionsleiste: Blitztabelle links, Trefferbild rechts |
| Persistenz | pro TV in `localStorage` (`tvDisplays`), kein Server-Zustand |

## 1. Menü „Anzeigen“

- Neuer Eintrag im Spielmenü-Modal (`openModal('Spielmenü', …)` in `app.js`): **„Anzeigen“**
  öffnet ein Untermenü mit drei Zeilen, umschaltbar mit ◀▶/Ⓐ wie im Einstellungs-Screen:
  - `Finish-Hilfe: Aus | 1-Dart | 2-Dart | 3-Dart` — nur sichtbar bei `gameMode` X01
    (fehlendes `gameMode` = X01)
  - `Trefferbild: Aus | An`
  - `Blitztabelle: Aus | An`
- Ⓑ/Zurück schließt das Untermenü zurück ins Spiel.
- Gespeichert als `tvDisplays = { finish: 0|1|2|3, heatmap: bool, ranking: bool }`
  (Default alles aus); Lesen/Schreiben in try/catch, bei Fehler Defaults.
- Code in neuer Datei `js/displays.js` (Einstellungen + Untermenü); `app.js` bekommt nur Hooks.

## 2. Finish-Hilfe (client-seitig)

**Neue Datei `js/checkout.js`** — reine Funktion ohne DOM/Browser-Abhängigkeiten
(auch unter Node ladbar):

```
Checkout.best(score, maxDarts, doubleOut) → Array<Segment> | null
Segment = { code: 'S20'|'D16'|'T19'|'B'|'BE', value: int, type: 'S'|'T'|'D'|'B'|'BE' }
Checkout.format(segments) → "T19 + 10 + D16" | "–"
```

**Kandidaten:** 62 Felder — S1–S20, D1–D20, T1–T20, Bull (25), Bullseye (50).
Bullseye zählt als Double (wie `Segment.isDouble()` im Server).

**Gültiger Weg:** 1…`maxDarts` Darts, Summe == `score`. Bei Double-Out muss der letzte Dart
ein Double (D1–D20 oder Bullseye) sein; Zwischenstände < 0 bzw. (Double-Out) == 1 sind
ausgeschlossen (würde Überwerfen bedeuten).

**Auswahl des besten Wegs** (lexikografisch):
1. wenigste Darts
2. kleinste Summe der Feldtyp-Kosten: Single 0 < Triple 1 < Double 2 < Bull 3 < Bullseye 4
   (Doubles am Rand → Wand-/Randtreffer-Risiko; Bull/Bullseye kleinste Ziele)
3. höherer Wert des ersten Darts, dann des zweiten

Reihenfolge der Darts: absteigend nach Wert; bei Double-Out steht das Double immer zuletzt.
Keine „Lieblings-Doubles“ (D16/D20) — bewusst einfach.

Referenzfälle: 45 → T15 · 30 → T10 · 18 → 18 · 40 → D20 · 50 → B-Eye ·
99 mit Double-Out → T20 + 19 + D10 · 2 mit Double-Out → D1 · 1 mit Double-Out → – ·
171 ohne Double-Out (3 Darts) → T20 + T20 + T17 · 61 ohne Double-Out (2 Darts) → T20 + 1.

**Anzeige-Regeln** (bei jedem gameUpdate neu berechnet, kein Zustand):
- `n = min(Modus, currentPlayer.freieWuerfe)`; ist `freieWuerfe` 0 (Spielzug beendet,
  wartet auf NEXT), wird keine Hilfe gezeigt.
- Sichtbar nur, wenn `punktestand` überhaupt mit `n` Darts erreichbar wäre, d.h.
  `punktestand <= n × 60` (ohne Double-Out) bzw. `<= 50 + (n−1) × 60` (mit Double-Out).
  Darüber: Zeile ausgeblendet. Darunter, aber kein Weg möglich: „–“.
- Double-In: ist `modeData.doubleIn == "true"` und `punktestand == startScore`, ist der
  Spieler noch nicht eröffnet → keine Hilfe.
- Fallback ohne `modeData` (anthrax oder älterer onion-server): X01 ohne Double-Out,
  ohne Double-In annehmen (entspricht der Schema-Regel „fehlt das Feld, gilt X01“).
- Formatierung: Single ohne Präfix („10“), „T19“, „D16“, „Bull“, „B-Eye“, getrennt durch „ + “.
- Platz: eigenes Element `#finishHint` zwischen `#galleryViewport` und `#gameButtons`,
  große Schrift (Akzentfarbe).

**Tests:** `src/test/js/checkout.test.js` mit `node --test` (eingebauter Runner, keine
Abhängigkeiten); Referenzfälle oben + Randfälle (0/negativ, 170/171/180, Double-Out-Grenzen).
Läuft nicht in CI (nur Gradle); Aufruf im onion-server README dokumentiert.

### Server-Voraussetzung: X01 sendet `gameMode` + `modeData`

Bisher schreibt `AnthraxJsonMapper.toJson` `gameMode`/`modeData` nur für Nicht-X01-Modi
(X01 „byte-kompatibel“ zu anthrax). Ohne `modeData.doubleOut` kann der Client aber nicht
zwischen 40 → D20 und 40 → 20 + 20 unterscheiden. Daher:

- Mapper schreibt `gameMode` und `modeData` **immer** (X01: `startScore`, `doubleIn`,
  `doubleOut` — liefert `X01Game.getSnapshot()` bereits).
- Rückwärtskompatibel: beide Felder sind im Schema optional; alle vorhandenen Clients
  prüfen `gameMode === 'CRICKET'` (`/display/`, `/tv/`), java-ui- und Python-Clients lesen
  die Felder nicht. `AnthraxJsonMapperTest` prüft künftig Vorhandensein + Inhalt statt
  Abwesenheit.
- `schema.json`: Beschreibung von `gameMode`/`modeData` ergänzen
  (onion-server sendet sie immer; X01-Schlüssel `startScore`, `doubleIn`, `doubleOut`).

## 3. Trefferbild (client-seitig)

- Neue Datei `js/heatmap.js`: baut einmalig ein SVG-Dartboard (20 Sektoren in Standard-
  Reihenfolge 20,1,18,4,13,6,10,15,2,17,3,19,7,16,8,11,14,9,12,5; Ringe: Single innen,
  Triple, Single außen, Double; Bull, Bullseye) mit adressierbaren Pfaden
  (`data-mult`, `data-field`).
- Daten: `currentPlayer.statistik.getroffeneFelder[mult-1][feld]` (Index 20 = Bull;
  Bullseye = `[1][20]`). Inner- und Außen-Single teilen denselben Zähler und dieselbe Farbe.
- Farbe: weiß bei 0 Treffern, sonst Blau mit Intensität `count / max` (max = meistgetroffenes
  Feld des Spielers = volles Blau). Linien/Zahlen dezent grau.
- Fehlt `getroffeneFelder` (optional im Schema) → leeres weißes Board.
- Darunter klein: Spielername.

## 4. Blitztabelle

### Server (onion-server, Java)

- `DartLikeGame` erhält `List<Player> getProjectedRanking()`: Platzierung „wenn das Spiel
  jetzt endete“.
  - X01 (`X01Game`): fertige Spieler in Zielreihenfolge, dann die übrigen nach Restpunkten
    aufsteigend, bei Gleichstand Wurfreihenfolge.
  - Cricket (`CricketScoreboard`/`CricketGame`): fertige Spieler zuerst, dann wie im
    vorhandenen Final-Ranking nach Punkten absteigend, Marks absteigend, Wurfreihenfolge.
  - Vor Spielstart (`scoreboard == null`): Wurfreihenfolge.
- `GameEngine` berechnet daraus je Spieler die prognostizierten Platzierungsstrafpunkte
  (`Platz + 1`, wie bei Spielende), **nur wenn** `houseRules.placementPenalty()` an ist und
  das Spiel noch läuft; sonst 0. Ist das Spiel beendet, sind die Strafpunkte bereits in
  `anzStrafpunkte` gebucht → Prognose 0.
- Zusätzlich, unabhängig von den Hausregeln (für die 0-Cent-Wertung):
  - `PlayerStatistics.summePlatzierungen`: Summe der Endplätze aller beendeten Spiele
    (Platz 1 = 1, …), bei Spielende gebucht. Landet automatisch in `statistik`.
  - prognostizierter Platz im laufenden Spiel (1…n), 0 nach Spielende.
- Transport: `EngineUpdate` bekommt `Map<Integer, Integer> projectedPlaces` und
  `Map<Integer, Integer> projectedPenalties` (Schlüssel: RegisteredPlayer-id; Penalties =
  Places wenn Platzierungsstrafe an, sonst leer); `AnthraxJsonMapper.spieler(...)` schreibt
  die optionalen Felder **`platzProjektion`** und **`strafpunkteProjektion`** (int, fehlt
  der Schlüssel → 0) in jedes Spieler-Objekt.
- `dart-server/schema.json`: Felder `spieler.strafpunkteProjektion`, `spieler.platzProjektion`
  und `statistik.summePlatzierungen` (integer, optional, onion-server) dokumentieren,
  **nicht** in `required`.
  Rückwärtskompatibel: `spieler` erlaubt Zusatzfelder (kein `additionalProperties: false`),
  alte Clients ignorieren das Feld; fehlt es (anthrax, ältere onion-server, Idle-Nachricht),
  gilt 0. Das Feld steht in allen Spieler-Objekten (`spielerPlatzierung`,
  `spielerReihenfolge`, `currentPlayer`), da alle über `spieler()` gebaut werden.
- Tests: `GameEngineTest` (Prognose bei laufendem X01-Spiel, nach Finish eines Spielers,
  Regel aus → 0, nach Spielende → 0), `AnthraxJsonMapperTest` (Feld wird geschrieben),
  X01/Cricket-Tests für `getProjectedRanking()`.

### Client

- Neue Datei `js/ranking.js`: Tabelle `Spieler | Σ | Balken` aus `spielerReihenfolge`.
  - Kosten > 0 (Strafpunkte-Wertung): `gebucht = statistik.anzStrafpunkte`,
    `prognose = strafpunkteProjektion ?? 0`; Σ-Spalte mit Betrag in € darunter
    (`Σ × kostenStrafpunkte / 100`).
  - Kosten 0 oder fehlend (Platzierungs-Wertung): `gebucht = statistik.summePlatzierungen ?? 0`,
    `prognose = platzProjektion ?? 0`; Spaltenkopf „Σ Plätze“, kein €.
  - Feature-Erkennung statt Versionsfeld: Platzierungs-Wertung, aber kein Spieler hat
    `statistik.summePlatzierungen` (anthrax, onion < 2.2) → Hinweis „Platzierungs-Wertung
    erfordert neueren Server“ statt einer Tabelle voller Nullen.
  - `Σ = gebucht + prognose`; Sortierung: Σ aufsteigend (weniger = besser),
    Gleichstand nach Wurfreihenfolge.
  - Balken: Breite relativ zum größten Σ; gebuchter Teil voll, Prognose-Teil hell.
  - Aktiver Spieler hervorgehoben.

## 5. Layout Spielanzeige

```
statusBar   (Server-Info links · Turnier-Info rechts)
galleryViewport  (flex: 1, schrumpft wenn Info-Zeile aktiv)
#finishHint      (nur wenn sichtbar)
#gameButtons
#displayRow      (nur wenn Trefferbild oder Blitztabelle an; ~35vh)
   [Blitztabelle links] [Trefferbild rechts]   — nur eine aktiv → diese zentriert
#gamePlacement
```

Nur auf `screen-game` mit laufendem Spiel; Lobby-Wartebild unverändert.

## 6. Nicht im Umfang (YAGNI)

- Triple-Out als Spielregel
- Schnapszahl-Warnung oder „bester Rest“-Vorschlag wenn kein Finish möglich
- Trefferbild pro Spiel, Rand-/Wandtreffer im Trefferbild
- Anzeigen im `/display/`-UI

## 7. Lieferung

- Branch `tv-displays` → PR → `master`; `version` in `onion-server/build.gradle` bumpen
  (2.1.1 → 2.2.0, neues Feature + Schema-Erweiterung).
- Reihenfolge (jeder Schritt einzeln nutzbar): Menü-Gerüst → Trefferbild →
  Blitztabelle (Server, dann Client) → Finish-Hilfe.
- Manuelle Prüfung: `./gradlew run` + `./gradlew processResources --continuous`,
  Würfe über Daneben/Weiter bzw. MQTT ohne Board.
