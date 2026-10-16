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
