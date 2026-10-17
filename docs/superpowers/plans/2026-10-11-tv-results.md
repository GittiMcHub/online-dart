# TV-Turnierergebnis Implementation Plan (client-only)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bare name lists on the TV results screen with a tile overview (final Blitztabelle + 5 achievements) and one table page per game — using only the existing MQTT gameUpdate messages.

**Architecture:** A pure module `results.js` records a snapshot per finished game from the gameUpdates the TV receives (persisted in `localStorage`) and derives tables, standings and achievements; `app.js` feeds updates into it and renders the paged results screen.

**Tech Stack:** plain JS (no build), `node --test` (Node ≥ 18), CSS.

**Spec:** `docs/superpowers/specs/2026-10-11-tv-results-design.md`

## Global Constraints

- **No server, `/api/state`, gameUpdate or `schema.json` changes.** Only files under `web/tv/` and `src/test/js/`.
- **Never run `git commit`.** The user commits.
- Commands run from `dart-server/onion-server`.
- Cost > 0 → values in € (`points × kosten / 100`, `"1,50 €"`); cost 0 → place mode (game value = place, sum = `summePlatzierungen`), plain numbers; unknown → `"–"`.
- Achievement text `"<value> – <Name>[, <Name>…]"`, averages one decimal with German comma; best 0 → `"–"`; `worst` = lowest value > 0, `null` if not lower than best; "Bester Spieler": worst = highest sum.
- UI texts German.

## Review Focus

1. **TV reloaded mid-tournament**: log survives via `localStorage`; a missed game shows "–" as game value and the "unvollständig" hint, sums stay right. → Task 1 test.
2. **New tournament with the same players**: old log must not leak into the new results (smaller `spielId` resets). → Task 1 test.
3. **Idle message after the tournament** (`gameState UNDEFINED`, no players) must not wipe the log. → Task 1 test.
4. **Player without darts in a game** (`avgSpiel` 0) is not the lowest Spiel-Avg. → Task 1 test.
5. **Layout at 1080p**: overview + three buttons without scrolling. → Task 2 manual check.

---

### Task 1: `results.js` (record + derive) with Node tests

**Files:** Create `src/main/resources/web/tv/js/results.js`, `src/test/js/results.test.js`

**Interfaces — Produces:** global `Results` with `record(log, update) → log`, `gameIds(log) → number[]`, `complete(log) → boolean`, `placesMode(log)`, `formatValue(points, log) → string`, `gameTable(log, spielId) → [{platz, name, spiel, turnier}]`, `standings(log) → [{name, total}]`, `achievements(log) → [{title, best, worst}]`.

- [ ] **Step 1: Failing tests** — `src/test/js/results.test.js`:

```js
const test = require('node:test');
const assert = require('node:assert');
const Results = require('../../main/resources/web/tv/js/results.js');

// gameUpdate wie vom Server; spieler: [name, strafpunkte, platzsumme, avgSpiel, avgTurnier, finish, aufnahme]
const ID = { Hans: 0, Bob: 1 };
function update(spielId, gameState, platzierung, spieler, kosten = 50) {
  const objekt = ([name, anzStrafpunkte, summePlatzierungen, avgSpiel, avgTurnier, highestFinish, maxPunkteProSpielzug]) => ({
    id: ID[name], name,
    statistik: { anzStrafpunkte, summePlatzierungen, avgSpiel, avgTurnier, highestFinish, maxPunkteProSpielzug }
  });
  const reihenfolge = spieler.map(objekt);
  return {
    spielId, anzahlSpiele: 2, kostenStrafpunkte: kosten, gameState,
    spielerReihenfolge: reihenfolge,
    spielerPlatzierung: platzierung.map((name) => reihenfolge.find((s) => s.name === name))
  };
}

// Spiel 1: Hans gewinnt (1 Strafpunkt), Bob Zweiter (3). Spiel 2: Bob gewinnt (+1),
// Hans Zweiter (+2) ohne Dart (avg 0).
const spiel1 = (kosten) => update(1, 'FINISHED', ['Hans', 'Bob'], [
  ['Hans', 1, 1, 60, 60, 40, 100], ['Bob', 3, 2, 40, 40, 0, 80]], kosten);
const spiel2laeuft = (kosten) => update(2, 'RUNNING', [], [
  ['Bob', 3, 2, 0, 40, 0, 80], ['Hans', 1, 1, 0, 60, 40, 100]], kosten);
const spiel2 = (kosten) => update(2, 'FINISHED', ['Bob', 'Hans'], [
  ['Bob', 4, 3, 70, 50, 0, 100], ['Hans', 3, 3, 0, 55.5, 40, 100]], kosten);

const turnier = (kosten = 50) =>
  [spiel1(kosten), spiel2laeuft(kosten), spiel2(kosten)].reduce(Results.record, null);

test('mitschreiben: ein Schnappschuss pro beendetem Spiel', () => {
  const log = turnier();
  assert.deepStrictEqual(Results.gameIds(log), [1, 2]);
  assert.strictEqual(Results.complete(log), true);
});

test('Tabelle pro Spiel: Strafpunkte als Differenz, Turnier = laufender Zähler', () => {
  const log = turnier();
  assert.deepStrictEqual(Results.gameTable(log, 1), [
    { platz: 1, name: 'Hans', spiel: 1, turnier: 1 },
    { platz: 2, name: 'Bob', spiel: 3, turnier: 3 }]);
  assert.deepStrictEqual(Results.gameTable(log, 2), [
    { platz: 1, name: 'Bob', spiel: 1, turnier: 4 },
    { platz: 2, name: 'Hans', spiel: 2, turnier: 3 }]);
  assert.strictEqual(Results.formatValue(3, log), '1,50 €');
  assert.strictEqual(Results.formatValue(null, log), '–');
});

test('0 Cent: Plätze statt Strafpunkte', () => {
  const log = turnier(0);
  assert.strictEqual(Results.placesMode(log), true);
  assert.deepStrictEqual(Results.gameTable(log, 2), [
    { platz: 1, name: 'Bob', spiel: 1, turnier: 3 },
    { platz: 2, name: 'Hans', spiel: 2, turnier: 3 }]);
  assert.strictEqual(Results.formatValue(3, log), '3');
});

test('Endstand aufsteigend, Gleichstand in Spieler-Reihenfolge', () => {
  assert.deepStrictEqual(Results.standings(turnier()), [{ name: 'Hans', total: 3 }, { name: 'Bob', total: 4 }]);
  assert.deepStrictEqual(Results.standings(turnier(0)), [{ name: 'Hans', total: 3 }, { name: 'Bob', total: 3 }]);
});

test('Achievements mit Bestwert und Gegenwert', () => {
  assert.deepStrictEqual(Results.achievements(turnier()), [
    { title: 'Bester Spieler', best: '1,50 € – Hans', worst: '2,00 € – Bob' },
    { title: 'Höchstes Finish', best: '40 – Hans', worst: null },          // Bob ohne Finish zählt nicht
    { title: 'Bester Spiel-Avg.', best: '70,0 – Bob', worst: '40,0 – Bob' }, // Avg 0 (kein Dart) zählt nicht
    { title: 'Bester Turnier-Avg.', best: '55,5 – Hans', worst: '50,0 – Bob' },
    { title: 'Höchste Aufnahme', best: '100 – Bob, Hans', worst: null }    // Gleichstand
  ]);
  const [besterSpieler] = Results.achievements(turnier(0));
  assert.deepStrictEqual(besterSpieler, { title: 'Bester Spieler', best: '3 – Hans, Bob', worst: null });
});

test('Idle-Nachricht nach Turnierende löscht das Log nicht', () => {
  const log = turnier();
  const idle = { spielId: 0, anzahlSpiele: 0, gameState: 'UNDEFINED', spielerReihenfolge: [], spielerPlatzierung: [] };
  assert.strictEqual(Results.record(log, idle), log);
});

test('neues Turnier mit denselben Spielern beginnt ein neues Log', () => {
  const log = Results.record(turnier(), update(1, 'RUNNING', [], [
    ['Hans', 3, 3, 0, 55.5, 40, 100], ['Bob', 4, 3, 0, 50, 0, 100]]));
  assert.deepStrictEqual(Results.gameIds(log), []);
});

test('TV erst ab Spiel 2 dabei: Spielwert unbekannt, Summe stimmt, unvollständig', () => {
  const log = [spiel2laeuft(50), spiel2(50)].reduce(Results.record, null);
  assert.deepStrictEqual(Results.gameIds(log), [2]);
  assert.strictEqual(Results.complete(log), false);
  assert.deepStrictEqual(Results.gameTable(log, 2), [
    { platz: 1, name: 'Bob', spiel: null, turnier: 4 },
    { platz: 2, name: 'Hans', spiel: null, turnier: 3 }]);
});

test('ohne Log: keine Spiele, leere Auswertung', () => {
  assert.deepStrictEqual(Results.gameIds(null), []);
  assert.deepStrictEqual(Results.standings(null), []);
});
```

- [ ] **Step 2: Run, expect failure** — `node --test src/test/js/` → `Cannot find module …/results.js`.

- [ ] **Step 3: Implement** — `src/main/resources/web/tv/js/results.js`:

```js
/* Turnierergebnis aus den gameUpdates, die der TV während des Turniers sieht
   (kein Server-Format nötig): pro beendetem Spiel ein Schnappschuss je Spieler.
   Kosten > 0: Strafpunkte in €; 0 Cent: Plätze wie die Blitztabelle.
   Reine Logik, auch unter Node ladbar. */
const Results = (() => {

  const playerKey = (data) => data.spielerReihenfolge.map((s) => s.name).sort().join('|');

  // neues Log bei neuer Spielerschaft, anderer Spielanzahl oder kleinerer spielId
  function record(log, data) {
    if (!data || !data.gameState || data.gameState === 'UNDEFINED'
        || !Array.isArray(data.spielerReihenfolge) || data.spielerReihenfolge.length === 0) {
      return log;
    }
    const key = playerKey(data);
    const fresh = !log || log.key !== key || log.anzahlSpiele !== data.anzahlSpiele
      || data.spielId < log.lastSpielId;
    const next = fresh
      ? { key, anzahlSpiele: data.anzahlSpiele, names: data.spielerReihenfolge.map((s) => s.name), games: {} }
      : { ...log, games: { ...log.games } };
    next.kosten = data.kostenStrafpunkte || 0;
    next.lastSpielId = data.spielId;
    next.final = data.spielerReihenfolge.map((s) => {
      const statistik = s.statistik || {};
      return {
        name: s.name,
        avgTurnier: statistik.avgTurnier || 0,
        highestFinish: statistik.highestFinish || 0,
        maxPunkteProSpielzug: statistik.maxPunkteProSpielzug || 0
      };
    });
    if (data.gameState === 'FINISHED') {
      const ranking = (data.spielerPlatzierung || []).map((s) => s.id);
      next.games[data.spielId] = data.spielerReihenfolge.map((s) => {
        const statistik = s.statistik || {};
        return {
          name: s.name,
          platz: ranking.indexOf(s.id) + 1,
          anzStrafpunkte: statistik.anzStrafpunkte || 0,
          summePlatzierungen: statistik.summePlatzierungen,
          avgSpiel: statistik.avgSpiel || 0
        };
      });
    }
    return next;
  }

  function gameIds(log) {
    return log ? Object.keys(log.games).map(Number).sort((a, b) => a - b) : [];
  }

  // keine Lücke: Spiele 1…max alle mitgeschrieben
  function complete(log) {
    const ids = gameIds(log);
    return ids.length === 0 || ids.length === ids[ids.length - 1];
  }

  function placesMode(log) {
    return !(log && log.kosten > 0);
  }

  function formatEuro(euro) {
    return euro.toFixed(2).replace('.', ',') + ' €';
  }

  function formatValue(points, log) {
    if (points === null || points === undefined) {
      return '–';
    }
    return placesMode(log) ? String(points) : formatEuro(points * log.kosten / 100);
  }

  function formatAvg(value) {
    return value.toFixed(1).replace('.', ',');
  }

  // laufende Summe am Spielende (Zähler des Servers, stimmt auch bei verpassten Spielen)
  function runningTotal(log, row) {
    if (!placesMode(log)) {
      return row.anzStrafpunkte;
    }
    return typeof row.summePlatzierungen === 'number' ? row.summePlatzierungen : null;
  }

  // Wert des einzelnen Spiels; Strafpunkte als Differenz zum Ende des Vorspiels
  function gameValue(log, spielId, row) {
    if (placesMode(log)) {
      return row.platz;
    }
    if (spielId === 1) {
      return row.anzStrafpunkte;
    }
    const before = (log.games[spielId - 1] || []).find((r) => r.name === row.name);
    return before ? row.anzStrafpunkte - before.anzStrafpunkte : null;
  }

  function gameTable(log, spielId) {
    return [...log.games[spielId]]
      .sort((a, b) => a.platz - b.platz)
      .map((row) => ({ platz: row.platz, name: row.name, spiel: gameValue(log, spielId, row), turnier: runningTotal(log, row) }));
  }

  function standings(log) {
    const ids = gameIds(log);
    if (ids.length === 0) {
      return [];
    }
    return log.games[ids[ids.length - 1]]
      .map((row) => ({ name: row.name, total: runningTotal(log, row) || 0, order: log.names.indexOf(row.name) }))
      .sort((a, b) => a.total - b.total || a.order - b.order)
      .map(({ name, total }) => ({ name, total }));
  }

  // entries: [{ name, value }] → { value, names } für pick = Math.max | Math.min
  function extreme(entries, pick) {
    if (entries.length === 0) {
      return null;
    }
    const value = pick(...entries.map((entry) => entry.value));
    const names = [...new Set(entries.filter((entry) => entry.value === value).map((entry) => entry.name))];
    return { value, names };
  }

  const line = (found, format) => format(found.value) + ' – ' + found.names.join(', ');

  // Bestwert = höchster Wert; Gegenwert = niedrigster echter Wert (> 0),
  // entfällt, wenn es keinen niedrigeren gibt
  function highTile(title, entries, format) {
    const best = extreme(entries, Math.max);
    if (!best || !(best.value > 0)) {
      return { title, best: '–', worst: null };
    }
    const worst = extreme(entries.filter((entry) => entry.value > 0), Math.min);
    return { title, best: line(best, format), worst: worst.value < best.value ? line(worst, format) : null };
  }

  function achievements(log) {
    const final = (log && log.final) || [];
    const of = (key) => final.map((s) => ({ name: s.name, value: s[key] }));
    const sums = standings(log).map((row) => ({ name: row.name, value: row.total }));
    const lowest = extreme(sums, Math.min);
    const highest = extreme(sums, Math.max);
    const value = (points) => formatValue(points, log);
    const gameAvgs = gameIds(log).flatMap((id) =>
      log.games[id].map((row) => ({ name: row.name, value: row.avgSpiel })));
    return [
      {
        title: 'Bester Spieler',
        best: lowest ? line(lowest, value) : '–',
        worst: lowest && highest.value > lowest.value ? line(highest, value) : null
      },
      highTile('Höchstes Finish', of('highestFinish'), String),
      highTile('Bester Spiel-Avg.', gameAvgs, formatAvg),
      highTile('Bester Turnier-Avg.', of('avgTurnier'), formatAvg),
      highTile('Höchste Aufnahme', of('maxPunkteProSpielzug'), String)
    ];
  }

  return { record, gameIds, complete, placesMode, formatValue, gameTable, standings, achievements };
})();

if (typeof module !== 'undefined') {
  module.exports = Results;
}
```

- [ ] **Step 4: Run** — `node --test src/test/js/` → all pass.

- [ ] **Step 5: Hand over** — `js/results.js`, `src/test/js/results.test.js`.

---

### Task 2: Results screen + recording hook

**Files:** Modify `web/tv/index.html`, `web/tv/js/app.js`, `web/tv/tv.css`; `README.md` (onion-server)

**Interfaces — Consumes:** `Results.*` (Task 1); existing `escapeHtml`, `item`, `legend`, `Focus.set(items, {onConfirm, onLeft, onRight})`, `Api.onGameUpdate`.

- [ ] **Step 1: HTML** — `#screen-results`: `<h1 id="resultsTitle">Ergebnis</h1>`, rest unchanged; add `<script src="js/results.js"></script>` before `js/app.js`.

- [ ] **Step 2: Record every update** — in `app.js` near the other `let`s:

```js
  let resultsLog = loadResultsLog(); // mitgeschriebene Spielenden fürs Ergebnis (results.js)
  let resultsPage = 0;               // 0 = Übersicht, k = k-tes mitgeschriebenes Spiel
```

helpers next to `loadNames`:

```js
  function loadResultsLog() {
    try {
      return JSON.parse(localStorage.getItem('tvResultsLog'));
    } catch (e) {
      return null;
    }
  }

  function saveResultsLog() {
    try {
      localStorage.setItem('tvResultsLog', JSON.stringify(resultsLog));
    } catch (e) {
      // ohne Speicher gilt das Log nur bis zum Neuladen
    }
  }
```

first lines of `Api.onGameUpdate((data) => {`:

```js
    const recorded = Results.record(resultsLog, data);
    if (recorded !== resultsLog) {
      resultsLog = recorded;
      saveResultsLog();
    }
```

In `show(name)` before `render();`: `if (name === 'results') resultsPage = 0;`.

- [ ] **Step 3: Render** — replace `renderResults()`:

```js
  function renderResults() {
    const ids = Results.gameIds(resultsLog);
    resultsPage = Math.min(resultsPage, ids.length);
    const box = $('resultsRanking');
    const gap = Results.complete(resultsLog)
      ? '' : '<p class="hint">Unvollständig: dieser TV hat nicht alle Spiele gesehen.</p>';
    if (ids.length === 0) {
      $('resultsTitle').textContent = 'Ergebnis';
      box.innerHTML = '<p class="hint">Keine Ergebnisse vorhanden.</p>';
    } else if (resultsPage === 0) {
      $('resultsTitle').textContent = 'Turnierergebnis';
      box.innerHTML = gap + resultsOverview();
    } else {
      $('resultsTitle').textContent = 'Spiel ' + ids[resultsPage - 1] + ' von ' + resultsLog.anzahlSpiele;
      box.innerHTML = gap + resultsGameTable(ids[resultsPage - 1]);
    }

    const list = $('resultsList');
    list.innerHTML = '';
    list.appendChild(item('Nochmal spielen', 'again', { action: 'again' }));
    list.appendChild(item('Zur Lobby', 'lobby', { action: 'lobby' }));
    list.appendChild(item('Hauptmenü', 'menu', { action: 'menu' }));

    Focus.set([...list.children], {
      onConfirm: resultsSelect,
      onLeft: () => resultsTurn(-1),
      onRight: () => resultsTurn(1)
    });
    legend(ids.length > 0 ? [['◀▶', 'Seite'], ['Ⓐ', 'Auswählen']] : [['Ⓐ', 'Auswählen']]);
  }

  // ◀▶ blättert: Übersicht → Spiele (ohne Umlauf)
  function resultsTurn(dir) {
    const pages = Results.gameIds(resultsLog).length + 1;
    const next = Math.max(0, Math.min(pages - 1, resultsPage + dir));
    if (next !== resultsPage) {
      resultsPage = next;
      renderResults();
    }
  }

  /* Übersicht: finale Blitztabelle (große Kachel) + Achievement-Kacheln */
  function resultsOverview() {
    const standings = Results.standings(resultsLog);
    const max = Math.max(1, ...standings.map((row) => row.total));
    const ranking = '<section class="results-tile results-ranking">'
      + '<h2 class="panel-title">Turnier Blitztabelle</h2><table class="ranking-table"><tbody>'
      + standings.map((row) => '<tr><td class="ranking-name">' + escapeHtml(row.name) + '</td>'
        + '<td class="ranking-sum">' + Results.formatValue(row.total, resultsLog) + '</td>'
        + '<td class="ranking-bar"><span class="bar-booked" style="width:' + (100 * row.total / max) + '%"></span></td></tr>')
        .join('')
      + '</tbody></table></section>';
    const tiles = Results.achievements(resultsLog).map((tile) =>
      '<section class="results-tile achievement"><h2 class="panel-title">' + tile.title + '</h2>'
      + '<div class="achievement-best">' + escapeHtml(tile.best) + '</div>'
      + (tile.worst ? '<div class="achievement-worst">' + escapeHtml(tile.worst) + '</div>' : '')
      + '</section>').join('');
    return '<div class="results-grid">' + ranking + tiles + '</div>';
  }

  /* Seite pro Spiel: Pos | Spieler | Spiel | Turnier (laufende Summe) */
  function resultsGameTable(spielId) {
    return '<table class="results-table"><thead><tr><th>Pos</th><th>Spieler</th><th>Spiel</th><th>Turnier</th></tr></thead><tbody>'
      + Results.gameTable(resultsLog, spielId).map((row) =>
        '<tr><td>' + row.platz + '</td><td>' + escapeHtml(row.name) + '</td>'
        + '<td>' + Results.formatValue(row.spiel, resultsLog) + '</td>'
        + '<td>' + Results.formatValue(row.turnier, resultsLog) + '</td></tr>').join('')
      + '</tbody></table>';
  }
```

- [ ] **Step 4: Styles** — append to `tv.css`:

```css
/* ################### Turnierergebnis ################### */

/* große Blitztabelle links über drei Reihen, Achievements rechts */
.results-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  grid-template-rows: repeat(3, 1fr);
  gap: 1.5vw;
  height: 48vh;
  margin-bottom: 3vh;
}

.results-tile {
  background: var(--panel);
  border-radius: 0.8vw;
  padding: 1.5vh 1.5vw;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.results-ranking {
  grid-row: 1 / span 3;
  justify-content: flex-start;
}

.achievement {
  text-align: center;
}

.achievement-best {
  font-weight: bold;
  color: var(--accent);
}

.achievement-worst {
  font-size: 0.6em;
  color: var(--muted);
  margin-top: 0.6vh;
}

.results-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 3vh;
}

.results-table th {
  color: var(--muted);
  font-weight: normal;
  text-align: left;
  padding: 1vh 1vw;
}

.results-table td {
  padding: 1vh 1vw;
  border-top: 1px solid #2a3040;
}

.results-table th:nth-child(n+3),
.results-table td:nth-child(n+3) {
  text-align: right;
}
```

- [ ] **Step 5: README** — append to the TV paragraph in `dart-server/onion-server/README.md`:

```markdown
Nach dem Turnier zeigt der Ergebnis-Screen eine Übersicht (Turnier-Blitztabelle,
Achievements wie Höchstes Finish oder Bester Spiel-Avg.) und mit ◀▶ je Spiel eine Tabelle
mit Platz, Wert des Spiels und laufender Turniersumme. Der TV schreibt dafür die Spielenden
selbst mit — hat er Spiele verpasst, fehlen deren Einzelwerte.
```

- [ ] **Step 6: Checks** — `node --check src/main/resources/web/tv/js/app.js`; `node --test src/test/js/` → all pass.

- [ ] **Step 7: Manual check** (separate test instance on ports 8430/1893/8093, never the user's server): 3 players, X01 301, 2 games, 50 Cent, one wall hit; play via `Api.publishControl` in offscreen Electron 1920×1080. Verify: overview tiles match tables; `#screen-results` `scrollHeight == clientHeight` (else lower `.results-grid` height); ▶/◀ paging; reload during game 2 keeps game 1; 0-Cent tournament shows numbers.

- [ ] **Step 8: Hand over** — `index.html`, `js/app.js`, `tv.css`, `README.md`.
