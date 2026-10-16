/* Blitztabelle: Turnier-Ranking, weniger ist besser, Gleichstand nach
   Wurfreihenfolge. Kosten > 0: gebuchte Strafpunkte (statistik.anzStrafpunkte)
   + Platzierungs-Prognose (strafpunkteProjektion). Kosten 0: nur Plätze —
   Summe der Endplätze (statistik.summePlatzierungen) + Platz im laufenden
   Spiel (platzProjektion). Fehlende Felder (anthrax) zählen 0. */
const Ranking = (() => {

  function placesMode(data) {
    return !(data && data.kostenStrafpunkte > 0);
  }

  function rows(data) {
    const places = placesMode(data);
    const cents = (data && data.kostenStrafpunkte) || 0;
    const players = (data && data.spielerReihenfolge) || [];
    return players
      .map((spieler, order) => {
        const statistik = spieler.statistik || {};
        const booked = (places ? statistik.summePlatzierungen : statistik.anzStrafpunkte) || 0;
        const projected = (places ? spieler.platzProjektion : spieler.strafpunkteProjektion) || 0;
        const total = booked + projected;
        return {
          id: spieler.id, name: spieler.name, booked, projected, total,
          euro: places ? null : total * cents / 100, order
        };
      })
      .sort((a, b) => a.total - b.total || a.order - b.order);
  }

  // Feature-Erkennung statt Versionsnummer: ältere Server (anthrax, onion < 2.2)
  // senden summePlatzierungen nicht — dann wäre die Platz-Tabelle nur Nullen
  function supported(data) {
    const players = (data && data.spielerReihenfolge) || [];
    if (!placesMode(data) || players.length === 0) {
      return true;
    }
    return players.some((spieler) => spieler.statistik && 'summePlatzierungen' in spieler.statistik);
  }

  function formatEuro(euro) {
    return euro.toFixed(2).replace('.', ',') + ' €';
  }

  // Summen-Spalte: bei Strafpunkt-Kosten nur der Betrag, sonst die Platzsumme
  function sumLabel(row) {
    return row.euro === null ? String(row.total) : formatEuro(row.euro);
  }

  // Bedeutung der zwei Balkenteile (voll, hell)
  function legend(data) {
    return placesMode(data)
      ? ['Plätze bisher', 'Platz laufendes Spiel']
      : ['gebucht', 'Prognose laufendes Spiel'];
  }

  function escapeHtml(text) {
    return String(text).replace(/[&<>"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  function render(container, data) {
    const title = '<h2 class="panel-title">Turnier Blitztabelle</h2>';
    if (!supported(data)) {
      container.innerHTML = title + '<p class="hint">Platzierungs-Wertung erfordert neueren Server</p>';
      return;
    }
    const list = rows(data);
    const max = Math.max(1, ...list.map((row) => row.total));
    const currentId = data.currentPlayer ? data.currentPlayer.id : null;
    const [bookedLabel, projectedLabel] = legend(data);
    container.innerHTML = title
      + '<table class="ranking-table"><tbody>'
      + list.map((row) =>
        '<tr' + (row.id === currentId ? ' class="active-player"' : '') + '>'
        + '<td class="ranking-name">' + escapeHtml(row.name) + '</td>'
        + '<td class="ranking-sum">' + sumLabel(row) + '</td>'
        + '<td class="ranking-bar">'
        + '<span class="bar-booked" style="width:' + (100 * row.booked / max) + '%"></span>'
        + '<span class="bar-projected" style="width:' + (100 * row.projected / max) + '%"></span>'
        + '</td></tr>').join('')
      + '</tbody></table>'
      + '<div class="ranking-legend">'
      + '<span><span class="bar-booked legend-swatch"></span>' + bookedLabel + '</span>'
      + '<span><span class="bar-projected legend-swatch"></span>' + projectedLabel + '</span>'
      + '</div>';
  }

  return { placesMode, rows, supported, formatEuro, sumLabel, legend, render };
})();

if (typeof module !== 'undefined') {
  module.exports = Ranking;
}
