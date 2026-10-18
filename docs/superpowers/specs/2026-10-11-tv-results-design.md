# TV-Turnierergebnis: Tabelle pro Spiel + Achievement-Kacheln — Design

Datum: 2026-10-11 · Komponente: TV-UI `dart-server/onion-server/src/main/resources/web/tv/`

## Ziel

Der Ergebnis-Screen der TV-Oberfläche (nach Turnierende) zeigt statt nackter Namenslisten:

1. **Seite „Turnierergebnis“**: große Kachel mit der finalen Turnier-Blitztabelle (wer zahlt wie
   viel, Abstand als Balken) + Achievement-Kacheln.
2. **Seiten „Spiel k von n“**: Tabelle `Pos | Spieler | Spiel | Turnier` mit dem Wert des Spiels
   und der laufenden Turnier-Summe (z. B. Spiel 1: `1 Hans 0,50 € 0,50 €`, Spiel 2:
   `1 Hans 0,50 € 1,00 €`).

**Randbedingung: keine Änderung am Server, an `/api/state` oder am gameUpdate-JSON/`schema.json`.**
Alles entsteht im TV aus den vorhandenen MQTT-`status/gameUpdate`-Nachrichten.

## Entscheidungen

| Thema | Entscheidung |
|---|---|
| Datenbasis | TV schreibt bei jedem Spielende (`gameState: FINISHED`) einen Schnappschuss je Spieler mit |
| Platz pro Spiel | Position in `spielerPlatzierung` der FINISHED-Nachricht |
| Strafpunkte pro Spiel | `anzStrafpunkte` am Spielende − Wert am Ende des Vorspiels (inkl. Wandtreffer, Schnapszahl, Platzierung) |
| Laufende Turniersumme | `anzStrafpunkte` (Kosten > 0) bzw. `summePlatzierungen` (0 Cent) am Spielende — stimmt auch, wenn der TV Vorspiele verpasst hat |
| Spiel-Avg. | `avgSpiel` am Spielende |
| Turnier-Werte | `avgTurnier`, `highestFinish`, `maxPunkteProSpielzug` aus der letzten Nachricht |
| Persistenz | `localStorage` `tvResultsLog` (übersteht Neuladen); neues Turnier = andere Spielerschaft, andere Spielanzahl oder kleinere `spielId` |
| Verpasste Spiele | Spielwert „–“, Hinweis „unvollständig“; Summen bleiben korrekt |
| Seitenaufteilung | Seite 0 = Kacheln, Seite 1…n = je ein mitgeschriebenes Spiel; ◀▶ blättert, ▲▼/Ⓐ Buttons |
| 0 Cent | Platzierungs-Wertung wie Blitztabelle: Spielwert = Platz, Summe = Platzsumme |
| „Höchste Aufnahme“ | `maxPunkteProSpielzug` |
| Gegenwert (3. Zeile) | niedrigster echter Wert (> 0); entfällt, wenn es keinen niedrigeren als den Bestwert gibt. „Bester Spieler“: Gegenwert = höchste Summe |

## Logik `js/results.js` (rein, unter Node testbar)

- `record(log, update) → log`: ignoriert Nachrichten ohne Spieler oder mit `gameState`
  `UNDEFINED`; beginnt ein neues Log bei neuem Turnier (s. o.); merkt sich Kosten, letzte
  `spielId`, Turnier-Werte je Spieler; bei `FINISHED` den Spiel-Schnappschuss
  `{name, platz, anzStrafpunkte, summePlatzierungen, avgSpiel}` je Spieler.
- `gameIds(log)`, `complete(log)` (keine Lücke 1…max), `placesMode(log)`,
  `formatValue(points, log)` (`"1,50 €"` / Zahl / `"–"` für unbekannt).
- `gameTable(log, spielId) → [{platz, name, spiel, turnier}]` nach Platz.
- `standings(log) → [{name, total}]` aus dem letzten mitgeschriebenen Spiel, aufsteigend.
- `achievements(log) → [{title, best, worst|null}]`: Bester Spieler, Höchstes Finish,
  Bester Spiel-Avg., Bester Turnier-Avg., Höchste Aufnahme; Text `"<Wert> – <Name>[, …]"`,
  Averages eine Nachkommastelle mit Komma, Bestwert 0 → `"–"`.

## Darstellung (`app.js`, `index.html`, `tv.css`)

- Titel „Turnierergebnis“ bzw. „Spiel k von n“; bei Lücken Hinweis unter dem Titel.
- Seite 0: Grid — große Kachel „Turnier Blitztabelle“ links über drei Reihen (Name | Summe |
  Balken), rechts 5 Achievement-Kacheln (Titel / Bestwert / klein-grau Gegenwert).
- Seite k: Tabelle `Pos | Spieler | Spiel | Turnier`.
- Buttons wie bisher; ◀▶ blättert ohne Umlauf; Fußleiste `◀▶ Seite`, `Ⓐ Auswählen`;
  beim Öffnen Seite 0. Kein Log → „Keine Ergebnisse vorhanden.“

## Nicht im Umfang

Server-/JSON-Änderungen, Ergebnisse von Spielen, die kein TV gesehen hat, Historie mehrerer
Turniere, Verwaltungsseite.
