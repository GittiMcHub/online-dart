const test = require('node:test');
const assert = require('node:assert');
const Ranking = require('../../main/resources/web/tv/js/ranking.js');

const spieler = (id, name, stat, projections) =>
  ({ id, name, statistik: stat, ...projections });

test('Kosten > 0: Strafpunkte gebucht + Prognose, aufsteigend sortiert', () => {
  const data = {
    kostenStrafpunkte: 50,
    spielerReihenfolge: [
      spieler(1, 'Alice', { anzStrafpunkte: 4, summePlatzierungen: 1 }, { strafpunkteProjektion: 1, platzProjektion: 1 }),
      spieler(2, 'Bob', { anzStrafpunkte: 2, summePlatzierungen: 2 }, { strafpunkteProjektion: 2, platzProjektion: 2 })
    ]
  };
  assert.strictEqual(Ranking.placesMode(data), false);
  const rows = Ranking.rows(data);
  assert.deepStrictEqual(rows.map((r) => [r.name, r.booked, r.projected, r.total]),
    [['Bob', 2, 2, 4], ['Alice', 4, 1, 5]]);
  assert.strictEqual(rows[0].euro, 2);
});

test('Kosten 0: nur Platzierungen zählen, Wand/Schnapszahl egal', () => {
  const data = {
    kostenStrafpunkte: 0,
    spielerReihenfolge: [
      // Alice: viele Strafpunkte, aber bisher immer vorn
      spieler(1, 'Alice', { anzStrafpunkte: 9, summePlatzierungen: 2 }, { strafpunkteProjektion: 0, platzProjektion: 2 }),
      spieler(2, 'Bob', { anzStrafpunkte: 0, summePlatzierungen: 4 }, { strafpunkteProjektion: 0, platzProjektion: 1 })
    ]
  };
  assert.strictEqual(Ranking.placesMode(data), true);
  const rows = Ranking.rows(data);
  assert.deepStrictEqual(rows.map((r) => [r.name, r.booked, r.projected, r.total]),
    [['Alice', 2, 2, 4], ['Bob', 4, 1, 5]]);
  assert.strictEqual(rows[0].euro, null);
});

test('fehlende Kosten gelten als 0 (Platzierungs-Wertung)', () => {
  assert.strictEqual(Ranking.placesMode({ spielerReihenfolge: [] }), true);
});

test('Gleichstand behält Wurfreihenfolge', () => {
  const rows = Ranking.rows({
    kostenStrafpunkte: 10,
    spielerReihenfolge: [
      spieler(1, 'A', { anzStrafpunkte: 3 }, {}),
      spieler(2, 'B', { anzStrafpunkte: 3 }, {})
    ]
  });
  assert.deepStrictEqual(rows.map((r) => r.name), ['A', 'B']);
});

test('ohne neue Felder (anthrax/ältere Server) keine NaN', () => {
  const strafen = Ranking.rows({
    kostenStrafpunkte: 10,
    spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3 } }]
  });
  assert.strictEqual(strafen[0].total, 3);
  const plaetze = Ranking.rows({
    kostenStrafpunkte: 0,
    spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3 } }]
  });
  assert.strictEqual(plaetze[0].total, 0);
  assert.deepStrictEqual(Ranking.rows({}), []);
});

test('Platzierungs-Wertung braucht summePlatzierungen vom Server', () => {
  const alt = { kostenStrafpunkte: 0, spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3 } }] };
  const neu = { kostenStrafpunkte: 0, spielerReihenfolge: [{ id: 1, name: 'A', statistik: { anzStrafpunkte: 3, summePlatzierungen: 0 } }] };
  const strafen = { kostenStrafpunkte: 10, spielerReihenfolge: alt.spielerReihenfolge };
  assert.strictEqual(Ranking.supported(alt), false);
  assert.strictEqual(Ranking.supported(neu), true);    // 0 ist ein echter Wert
  assert.strictEqual(Ranking.supported(strafen), true); // Strafpunkte gibt es immer
  assert.strictEqual(Ranking.supported({}), true);      // keine Spieler: nichts Irreführendes
});

test('Euro-Format deutsch', () => {
  assert.strictEqual(Ranking.formatEuro(2), '2,00 €');
  assert.strictEqual(Ranking.formatEuro(0.5), '0,50 €');
});

test('Summen-Spalte: € bei Strafpunkt-Kosten, Zahl bei Platzierungs-Wertung', () => {
  const kosten = { kostenStrafpunkte: 50, spielerReihenfolge: [spieler(1, 'A', { anzStrafpunkte: 3 }, { strafpunkteProjektion: 1 })] };
  const plaetze = { kostenStrafpunkte: 0, spielerReihenfolge: [spieler(1, 'A', { summePlatzierungen: 3 }, { platzProjektion: 1 })] };
  assert.strictEqual(Ranking.sumLabel(Ranking.rows(kosten)[0]), '2,00 €');
  assert.strictEqual(Ranking.sumLabel(Ranking.rows(plaetze)[0]), '4');
});

test('Legende passt zur Wertung', () => {
  assert.deepStrictEqual(Ranking.legend({ kostenStrafpunkte: 50 }), ['gebucht', 'Prognose laufendes Spiel']);
  assert.deepStrictEqual(Ranking.legend({ kostenStrafpunkte: 0 }), ['Plätze bisher', 'Platz laufendes Spiel']);
});

test('render: Titel, nur € in der Summe, Legende', () => {
  const container = { innerHTML: '' };
  Ranking.render(container, {
    kostenStrafpunkte: 50,
    currentPlayer: { id: 1 },
    spielerReihenfolge: [spieler(1, 'Alice', { anzStrafpunkte: 4 }, { strafpunkteProjektion: 1 })]
  });
  assert.match(container.innerHTML, /<h2[^>]*>Turnier Blitztabelle<\/h2>/);
  assert.match(container.innerHTML, /2,50 €/);
  assert.doesNotMatch(container.innerHTML, /class="ranking-sum">5/);
  assert.match(container.innerHTML, /gebucht/);
  assert.match(container.innerHTML, /Prognose laufendes Spiel/);
});
