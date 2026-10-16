/* Finish-Hilfe: bester Weg zum Checkout mit höchstens n Darts.
   Reihenfolge: wenigste Darts, dann geringste Feldtyp-Kosten (Single < Triple
   < Double < Bull < Bullseye — Doubles liegen am Rand, Bull ist klein), dann
   höchster erster Dart. Reine Logik ohne DOM, auch unter Node ladbar. */
const Checkout = (() => {

  const TYPE_COST = { S: 0, T: 1, D: 2, B: 3, BE: 4 };

  const SEGMENTS = [];
  for (let n = 1; n <= 20; n++) {
    SEGMENTS.push({ code: 'S' + n, value: n, type: 'S' });
    SEGMENTS.push({ code: 'T' + n, value: 3 * n, type: 'T' });
    SEGMENTS.push({ code: 'D' + n, value: 2 * n, type: 'D' });
  }
  SEGMENTS.push({ code: 'B', value: 25, type: 'B' });
  SEGMENTS.push({ code: 'BE', value: 50, type: 'BE' });

  // Bullseye zählt wie im Server (Segment.isDouble) als Double
  const isDouble = (segment) => segment.type === 'D' || segment.type === 'BE';

  const cost = (path) => path.reduce((sum, segment) => sum + TYPE_COST[segment.type], 0);

  // Anzeige-Reihenfolge: absteigend nach Wert, bei Double-Out das Double zuletzt
  function normalize(path, doubleOut) {
    if (!doubleOut) {
      return [...path].sort((a, b) => b.value - a.value);
    }
    const last = path[path.length - 1];
    return [...path.slice(0, -1).sort((a, b) => b.value - a.value), last];
  }

  function better(a, b) {
    const costDiff = cost(a) - cost(b);
    if (costDiff !== 0) {
      return costDiff < 0;
    }
    for (let i = 0; i < a.length; i++) {
      if (a[i].value !== b[i].value) {
        return a[i].value > b[i].value;
      }
    }
    return false;
  }

  function search(rest, dartsLeft, path, doubleOut, onFound) {
    if (dartsLeft === 1) {
      for (const segment of SEGMENTS) {
        if (segment.value === rest && (!doubleOut || isDouble(segment))) {
          onFound([...path, segment]);
        }
      }
      return;
    }
    for (const segment of SEGMENTS) {
      if (segment.value < rest) {
        search(rest - segment.value, dartsLeft - 1, [...path, segment], doubleOut, onFound);
      }
    }
  }

  function best(score, maxDarts, doubleOut) {
    if (!Number.isInteger(score) || score <= 0 || !(maxDarts >= 1)) {
      return null;
    }
    for (let darts = 1; darts <= Math.min(maxDarts, 3); darts++) {
      let winner = null;
      search(score, darts, [], doubleOut, (path) => {
        const candidate = normalize(path, doubleOut);
        if (!winner || better(candidate, winner)) {
          winner = candidate;
        }
      });
      if (winner) {
        return winner;
      }
    }
    return null;
  }

  function label(segment) {
    switch (segment.type) {
      case 'S': return String(segment.value);
      case 'B': return 'Bull';
      case 'BE': return 'B-Eye';
      default: return segment.code;
    }
  }

  // Boardreihenfolge im Uhrzeigersinn ab 20 (oben), je 18°
  const BOARD_ORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
  const ARROWS = ['↑', '↗', '→', '↘', '↓', '↙', '←', '↖'];

  // Wohin der Dart muss, vom Bull aus gesehen: 8 Richtungen à 45°
  function aim(segment) {
    if (segment.type === 'B') {
      return '◎';
    }
    if (segment.type === 'BE') {
      return '☉';
    }
    const degrees = BOARD_ORDER.indexOf(Number(segment.code.slice(1))) * 18;
    return ARROWS[Math.round(degrees / 45) % ARROWS.length];
  }

  function format(path) {
    return path ? path.map(label).join(' + ') : '–';
  }

  // höchste mit n Darts erreichbare Restpunktzahl
  function maxReach(darts, doubleOut) {
    return doubleOut ? 50 + (darts - 1) * 60 : darts * 60;
  }

  /* Text für die Finish-Hilfe-Zeile: null = Zeile belegt keinen Platz (aus,
     kein X01, keine Daten); '' = Platz reserviert, aber gerade kein Hinweis
     (Zug beendet, außer Reichweite, Double-In offen) — so springen die Buttons
     nicht. Ohne gameMode/modeData (anthrax) gilt X01 ohne Double-In/-Out. */
  function hintFor(data, mode) {
    if (!mode || !data || !data.currentPlayer) {
      return null;
    }
    if (data.gameMode && data.gameMode !== 'X01') {
      return null;
    }
    const rules = data.modeData || {};
    const player = data.currentPlayer;
    const darts = Math.min(mode, player.freieWuerfe || 0);
    if (darts < 1) {
      return '';
    }
    const score = player.punktestand;
    // Double-In: der Stand sinkt erst nach dem eröffnenden Double
    if (rules.doubleIn === 'true' && String(score) === rules.startScore) {
      return '';
    }
    const doubleOut = rules.doubleOut === 'true';
    if (!(score > 0) || score > maxReach(darts, doubleOut)) {
      return '';
    }
    const path = best(score, darts, doubleOut);
    // Pfeil nur für den nächsten Dart
    return path ? aim(path[0]) + ' ' + format(path) : format(path);
  }

  return { best, format, aim, hintFor };
})();

if (typeof module !== 'undefined') {
  module.exports = Checkout;
}
