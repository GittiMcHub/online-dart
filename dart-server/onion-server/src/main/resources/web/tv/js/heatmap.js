/* Trefferbild: SVG-Dartboard des aktiven Spielers, Felder blau nach Anzahl
   Treffer (meistgetroffenes Feld = volles Blau). Datenbasis ist
   statistik.getroffeneFelder [mult-1][feld-1], Index 20 = Bull — ganzes
   Turnier; inneres und äußeres Single teilen einen Zähler. */
const Heatmap = (() => {

  const ORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
  // Radien in mm nach Turnier-Norm
  const R = { bullseye: 6.35, bull: 15.9, tripleIn: 99, tripleOut: 107, doubleIn: 162, doubleOut: 170, numbers: 186 };
  const NS = 'http://www.w3.org/2000/svg';

  function countFor(felder, mult, field) {
    const row = felder && felder[mult - 1];
    if (!row) {
      return 0;
    }
    return row[field === 25 ? 20 : field - 1] || 0;
  }

  function colour(count, max) {
    if (!count || !max) {
      return '#9aa4b51f';
    }
    const lightness = Math.round(92 - 57 * (count / max));
    return 'hsl(210, 85%, ' + lightness + '%)';
  }

  function formatAvg(value) {
    return typeof value === 'number' ? value.toFixed(1).replace('.', ',') : '–';
  }

  // Fußzeile unter dem Board: 3-Dart-Averages des aktiven Spielers
  function averages(spieler) {
    const statistik = (spieler && spieler.statistik) || {};
    return 'Avg. Spiel ' + formatAvg(statistik.avgSpiel)
      + ' · Avg. Turnier ' + formatAvg(statistik.avgTurnier);
  }

  function point(r, deg) {
    const a = deg * Math.PI / 180;
    return (r * Math.cos(a)).toFixed(2) + ' ' + (r * Math.sin(a)).toFixed(2);
  }

  function ringSegment(r1, r2, a1, a2) {
    return 'M ' + point(r2, a1) + ' A ' + r2 + ' ' + r2 + ' 0 0 1 ' + point(r2, a2)
      + ' L ' + point(r1, a2) + ' A ' + r1 + ' ' + r1 + ' 0 0 0 ' + point(r1, a1) + ' Z';
  }

  function svgEl(name, attrs) {
    const el = document.createElementNS(NS, name);
    Object.entries(attrs).forEach(([key, value]) => el.setAttribute(key, value));
    return el;
  }

  function build(container) {
    const svg = svgEl('svg', { viewBox: '-200 -200 400 400', class: 'heatmap-board' });
    ORDER.forEach((field, i) => {
      // 20 oben, im Uhrzeigersinn, je 18°
      const center = -90 + i * 18;
      [[R.bull, R.tripleIn, 1], [R.tripleIn, R.tripleOut, 3],
       [R.tripleOut, R.doubleIn, 1], [R.doubleIn, R.doubleOut, 2]].forEach(([r1, r2, mult]) => {
        svg.appendChild(svgEl('path', {
          d: ringSegment(r1, r2, center - 9, center + 9),
          'data-mult': mult, 'data-field': field
        }));
      });
      const [x, y] = point(R.numbers, center).split(' ');
      const label = svgEl('text', { x, y, class: 'heatmap-number' });
      label.textContent = field;
      svg.appendChild(label);
    });
    svg.appendChild(svgEl('circle', { r: R.bull, 'data-mult': 1, 'data-field': 25 }));
    svg.appendChild(svgEl('circle', { r: R.bullseye, 'data-mult': 2, 'data-field': 25 }));
    const title = document.createElement('h2');
    title.className = 'panel-title';
    const caption = document.createElement('div');
    caption.className = 'heatmap-caption';
    container.innerHTML = '';
    container.appendChild(title);
    container.appendChild(svg);
    container.appendChild(caption);
  }

  function render(container, spieler) {
    if (!container.querySelector('svg')) {
      build(container);
    }
    const felder = spieler && spieler.statistik ? spieler.statistik.getroffeneFelder : undefined;
    const max = Array.isArray(felder) ? Math.max(0, ...felder.flat()) : 0;
    container.querySelectorAll('[data-mult]').forEach((el) => {
      const count = countFor(felder, Number(el.dataset.mult), Number(el.dataset.field));
      el.setAttribute('fill', colour(count, max));
    });
    container.querySelector('.panel-title').textContent =
      'Trefferbild: ' + (spieler ? spieler.name : '');
    container.querySelector('.heatmap-caption').textContent = averages(spieler);
  }

  return { countFor, colour, averages, render };
})();

if (typeof module !== 'undefined') {
  module.exports = Heatmap;
}
