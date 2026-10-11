const test = require('node:test');
const assert = require('node:assert');
const Heatmap = require('../../main/resources/web/tv/js/heatmap.js');

const empty = () => [0, 1, 2].map(() => new Array(21).fill(0));

test('countFor liest [mult-1][feld-1], Bull/Bullseye auf Index 20', () => {
  const felder = empty();
  felder[2][19] = 4;  // T20
  felder[0][20] = 2;  // Bull
  felder[1][20] = 1;  // Bullseye
  assert.strictEqual(Heatmap.countFor(felder, 3, 20), 4);
  assert.strictEqual(Heatmap.countFor(felder, 1, 25), 2);
  assert.strictEqual(Heatmap.countFor(felder, 2, 25), 1);
  assert.strictEqual(Heatmap.countFor(felder, 1, 5), 0);
});

test('countFor ohne Daten ist 0', () => {
  assert.strictEqual(Heatmap.countFor(undefined, 3, 20), 0);
  assert.strictEqual(Heatmap.countFor([], 3, 20), 0);
});

test('colour: dezentes Grau ohne Treffer, Blau kräftiger mit mehr Anteil', () => {
  assert.strictEqual(Heatmap.colour(0, 0), '#9aa4b51f');
  assert.strictEqual(Heatmap.colour(0, 5), '#9aa4b51f');
  assert.strictEqual(Heatmap.colour(5, 5), 'hsl(210, 85%, 35%)');
  assert.notStrictEqual(Heatmap.colour(1, 5), Heatmap.colour(5, 5));
});

test('Averages deutsch mit einer Nachkommastelle, fehlend als Strich', () => {
  assert.strictEqual(Heatmap.averages({ statistik: { avgSpiel: 64.07143, avgTurnier: 100.33333 } }),
    'Avg. Spiel 64,1 · Avg. Turnier 100,3');
  assert.strictEqual(Heatmap.averages({ statistik: { avgSpiel: 0, avgTurnier: 0 } }),
    'Avg. Spiel 0,0 · Avg. Turnier 0,0');
  assert.strictEqual(Heatmap.averages(undefined), 'Avg. Spiel – · Avg. Turnier –');
});
