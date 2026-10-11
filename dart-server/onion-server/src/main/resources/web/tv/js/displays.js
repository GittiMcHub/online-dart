/* Anzeigen-Einstellungen der Spielanzeige (Finish-Hilfe, Trefferbild,
   Blitztabelle, Sichtbarkeit) — pro TV in localStorage, kein Server-Zustand. */
const Displays = (() => {

  const KEY = 'tvDisplays';
  const DEFAULTS = { finish: 0, heatmap: false, ranking: false, opacity: 100 };
  const FINISH_LABELS = ['Aus', '1-Dart', '2-Dart', '3-Dart'];
  const OPACITIES = [25, 50, 75, 100];

  let state = load();

  // gespeicherte Werte prüfen: ein kaputter Eintrag darf kein „undefined“ zeigen
  function load() {
    let stored = {};
    try {
      stored = JSON.parse(localStorage.getItem(KEY)) || {};
    } catch (e) {
      // kaputter oder fehlender Speicher: Standardwerte
    }
    return {
      finish: FINISH_LABELS[stored.finish] !== undefined ? stored.finish : DEFAULTS.finish,
      heatmap: stored.heatmap === true,
      ranking: stored.ranking === true,
      opacity: OPACITIES.includes(stored.opacity) ? stored.opacity : DEFAULTS.opacity
    };
  }

  function save() {
    try {
      localStorage.setItem(KEY, JSON.stringify(state));
    } catch (e) {
      // ohne Speicher gilt die Wahl nur bis zum Neuladen
    }
  }

  function get() {
    return { ...state };
  }

  function step(values, current, dir) {
    return values[(values.indexOf(current) + dir + values.length) % values.length];
  }

  function cycle(key, dir) {
    if (key === 'finish') {
      state.finish = (state.finish + dir + FINISH_LABELS.length) % FINISH_LABELS.length;
    } else if (key === 'opacity') {
      state.opacity = step(OPACITIES, state.opacity, dir);
    } else {
      state[key] = !state[key];
    }
    save();
  }

  // Zeilen fürs Untermenü: Bezeichnung links, aktueller Wert rechts.
  // Finish-Hilfe nur bei X01
  function entries(x01) {
    const onOff = (key) => () => (state[key] ? 'An' : 'Aus');
    const list = [];
    if (x01) {
      list.push({ key: 'finish', label: 'Finish-Hilfe', value: () => FINISH_LABELS[state.finish] });
    }
    list.push({ key: 'heatmap', label: 'Trefferbild', value: onOff('heatmap') });
    list.push({ key: 'ranking', label: 'Blitztabelle', value: onOff('ranking') });
    list.push({ key: 'opacity', label: 'Sichtbarkeit', value: () => state.opacity + ' %' });
    return list;
  }

  return { get, cycle, entries };
})();

if (typeof module !== 'undefined') {
  module.exports = Displays;
}
