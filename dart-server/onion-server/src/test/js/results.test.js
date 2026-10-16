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
