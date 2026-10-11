const test = require('node:test');
const assert = require('node:assert');

// displays.js liest localStorage beim Laden: pro Test frisch laden
function loadDisplays(stored) {
  const store = stored === undefined ? {} : { tvDisplays: stored };
  global.localStorage = {
    getItem: (key) => (key in store ? store[key] : null),
    setItem: (key, value) => { store[key] = value; }
  };
  const path = require.resolve('../../main/resources/web/tv/js/displays.js');
  delete require.cache[path];
  return { Displays: require(path), store };
}

const rows = (Displays, x01) => Displays.entries(x01).map((e) => [e.label, e.value()]);

test('Einträge mit Wert rechts, Sichtbarkeit Standard 100 %', () => {
  const { Displays } = loadDisplays();
  assert.deepStrictEqual(rows(Displays, true), [
    ['Finish-Hilfe', 'Aus'], ['Trefferbild', 'Aus'], ['Blitztabelle', 'Aus'], ['Sichtbarkeit', '100 %']
  ]);
  assert.strictEqual(Displays.get().opacity, 100);
});

test('Cricket: keine Finish-Hilfe', () => {
  const { Displays } = loadDisplays();
  assert.deepStrictEqual(rows(Displays, false).map(([label]) => label), ['Trefferbild', 'Blitztabelle', 'Sichtbarkeit']);
});

test('Sichtbarkeit rotiert 25 → 50 → 75 → 100 und zurück, wird gespeichert', () => {
  const { Displays, store } = loadDisplays();
  Displays.cycle('opacity', 1);
  assert.strictEqual(Displays.get().opacity, 25);
  Displays.cycle('opacity', 1);
  assert.strictEqual(Displays.get().opacity, 50);
  Displays.cycle('opacity', -1);
  Displays.cycle('opacity', -1);
  assert.strictEqual(Displays.get().opacity, 100);
  assert.strictEqual(JSON.parse(store.tvDisplays).opacity, 100);
});

test('ungültige gespeicherte Werte fallen auf Standard zurück', () => {
  const { Displays } = loadDisplays(JSON.stringify({ finish: 'x', opacity: 33, heatmap: true }));
  assert.deepStrictEqual(Displays.get(), { finish: 0, heatmap: true, ranking: false, opacity: 100 });
  assert.strictEqual(Displays.entries(true)[0].value(), 'Aus');
});

test('kaputter Speicher: Standardwerte', () => {
  const { Displays } = loadDisplays('{kaputt');
  assert.deepStrictEqual(Displays.get(), { finish: 0, heatmap: false, ranking: false, opacity: 100 });
});
