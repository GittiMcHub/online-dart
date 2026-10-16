// Aufruf: node --test src/test/js/   (aus dart-server/onion-server)
const test = require('node:test');
const assert = require('node:assert');
const Checkout = require('../../main/resources/web/tv/js/checkout.js');

const hint = (score, darts, doubleOut) => Checkout.format(Checkout.best(score, darts, doubleOut));

test('1-Dart-Referenzfälle ohne Double-Out', () => {
  assert.strictEqual(hint(45, 1, false), 'T15');
  assert.strictEqual(hint(30, 1, false), 'T10');   // Triple vor Double
  assert.strictEqual(hint(18, 1, false), '18');    // Single vor Double/Triple
  assert.strictEqual(hint(40, 1, false), 'D20');
  assert.strictEqual(hint(50, 1, false), 'B-Eye');
  assert.strictEqual(hint(25, 1, false), 'Bull');
  assert.strictEqual(hint(59, 1, false), '–');
});

test('wenigste Darts vor Feldtyp', () => {
  assert.strictEqual(hint(30, 2, false), 'T10');   // nicht 20 + 10
  assert.strictEqual(hint(18, 3, false), '18');
});

test('Mehr-Dart-Wege', () => {
  assert.strictEqual(hint(61, 2, false), 'T20 + 1');
  assert.strictEqual(hint(171, 3, false), 'T20 + T20 + T17');
  assert.strictEqual(hint(180, 3, false), 'T20 + T20 + T20');
  assert.strictEqual(hint(181, 3, false), '–');
});

test('Double-Out', () => {
  assert.strictEqual(hint(99, 3, true), 'T20 + 19 + D10');
  assert.strictEqual(hint(2, 1, true), 'D1');
  assert.strictEqual(hint(1, 3, true), '–');
  assert.strictEqual(hint(50, 1, true), 'B-Eye');  // Bullseye zählt als Double
  assert.strictEqual(hint(45, 1, true), '–');
  assert.strictEqual(hint(170, 3, true), 'T20 + T20 + B-Eye');
  assert.strictEqual(hint(171, 3, true), '–');
});

test('ungültige Eingaben', () => {
  assert.strictEqual(Checkout.best(0, 3, false), null);
  assert.strictEqual(Checkout.best(-5, 3, false), null);
  assert.strictEqual(Checkout.best(20, 0, false), null);
});

const update = (punktestand, freieWuerfe, modeData) => ({
  gameMode: modeData ? 'X01' : undefined,
  modeData,
  currentPlayer: { punktestand, freieWuerfe }
});
const X01 = (doubleOut, doubleIn, startScore) =>
  ({ startScore: String(startScore || 301), doubleIn: String(!!doubleIn), doubleOut: String(!!doubleOut) });

test('hintFor: Modus und freie Darts', () => {
  assert.strictEqual(Checkout.hintFor(update(45, 3, X01(false)), 0), null);         // aus
  assert.strictEqual(Checkout.hintFor(update(45, 3, X01(false)), 1), '↘ T15');
  assert.strictEqual(Checkout.hintFor(update(99, 3, X01(true)), 3), '↑ T20 + 19 + D10');
  assert.strictEqual(Checkout.hintFor(update(99, 1, X01(true)), 3), '');           // 1 Dart übrig, > 50: Platz bleibt reserviert
  assert.strictEqual(Checkout.hintFor(update(30, 2, X01(false)), 3), '→ T10');
  assert.strictEqual(Checkout.hintFor(update(61, 3, X01(false)), 1), '');          // > 60 bei 1-Dart: Platz bleibt reserviert
});

test('hintFor: unmöglich innerhalb Reichweite zeigt Strich', () => {
  assert.strictEqual(Checkout.hintFor(update(59, 1, X01(false)), 1), '–');
  assert.strictEqual(Checkout.hintFor(update(1, 3, X01(true)), 3), '–');
});

test('hintFor: Spielzug beendet (WAITING) leert die Zeile, Platz bleibt', () => {
  assert.strictEqual(Checkout.hintFor(update(40, 0, X01(false)), 3), '');
});

test('hintFor: Double-In noch nicht eröffnet', () => {
  assert.strictEqual(Checkout.hintFor(update(101, 3, X01(false, true, 101)), 3), '');
  assert.strictEqual(Checkout.hintFor(update(100, 3, X01(false, true, 101)), 3), '↑ T20 + D20');
});

test('hintFor: ohne modeData (anthrax) = X01 ohne Double-Out', () => {
  assert.strictEqual(Checkout.hintFor(update(40, 3, undefined), 3), '↑ D20');
  assert.strictEqual(Checkout.hintFor(update(41, 3, undefined), 3), '→ T13 + 2');
});

test('hintFor: Aus, Cricket und fehlende Daten belegen keinen Platz (null)', () => {
  assert.strictEqual(Checkout.hintFor({ gameMode: 'CRICKET', currentPlayer: { punktestand: 20, freieWuerfe: 3 } }, 3), null);
  assert.strictEqual(Checkout.hintFor(null, 3), null);
  assert.strictEqual(Checkout.hintFor({}, 3), null);
});

test('aim: Richtung des Zielfelds vom Bull aus, 8 Richtungen à 45°', () => {
  const aim = (code) => Checkout.aim({ code, type: code.startsWith('B') ? code : code[0], value: 0 });
  const expected = {
    20: '↑', 1: '↑', 18: '↗', 4: '↗', 13: '→', 6: '→', 10: '→', 15: '↘', 2: '↘',
    17: '↓', 3: '↓', 19: '↓', 7: '↙', 16: '↙', 8: '←', 11: '←', 14: '←', 9: '↖', 12: '↖', 5: '↑'
  };
  for (const [field, arrow] of Object.entries(expected)) {
    assert.strictEqual(aim('S' + field), arrow, 'S' + field);
    assert.strictEqual(aim('T' + field), arrow, 'T' + field);
    assert.strictEqual(aim('D' + field), arrow, 'D' + field);
  }
  assert.strictEqual(aim('B'), '◎');
  assert.strictEqual(aim('BE'), '☉');
});

test('hintFor: Pfeil nur für den nächsten Dart, Bull-Symbole', () => {
  assert.strictEqual(Checkout.hintFor(update(50, 1, X01(true)), 1), '☉ B-Eye');
  assert.strictEqual(Checkout.hintFor(update(25, 1, X01(false)), 1), '◎ Bull');
  assert.strictEqual(Checkout.hintFor(update(16, 3, X01(false)), 3), '↙ 16');
});
