/* TV-App: Screen-Zustandsmaschine + Renderer. SSE (/api/events) treibt die
   App-Screens, MQTT die Spielanzeige (siehe api.js). Alle Eingaben kommen als
   'tv-action'-Events aus input.js und werden hier geroutet:
   Bildschirmtastatur > Modal > Spielanzeige > Fokusliste des Screens. */
(() => {

  const $ = (id) => document.getElementById(id);

  const SCREENS = ['boot', 'menu', 'boards', 'remote', 'lobby', 'settings', 'game', 'results'];

  let screen = 'boot';
  let appState = null;        // letzter SSE-Zustand
  let lastGame = null;        // letztes gameUpdate (MQTT)
  let mqttStarted = false;
  let brokerAutoTried = false;
  let wasRunning = false;     // Turnier lief -> bei IDLE zur Ergebnisanzeige
  let spectating = false;     // "Nur Anzeige": kein Wechsel zur Ergebnisanzeige
  let modal = null;           // {buttons: [{label, onSelect}], index}
  const boardIdChoice = {};   // gewünschte Board-ID je MAC vor dem Verbinden

  // ################### Einstellungen (localStorage) ###################

  const DEFAULT_SETTINGS = {
    gameMode: 'x01',
    startScore: 301,
    doubleIn: false,
    doubleOut: false,
    games: 1,
    penaltyCostCents: 0,
    houseRules: { schnapszahlPenalty: true, wallHitPenalty: true, placementPenalty: true }
  };
  let settings = loadJson('tvSettings', DEFAULT_SETTINGS);

  function loadJson(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? { ...fallback, ...JSON.parse(raw) } : { ...fallback };
    } catch (e) {
      return { ...fallback };
    }
  }

  function saveSettings() {
    localStorage.setItem('tvSettings', JSON.stringify(settings));
  }

  function loadNames() {
    try {
      return JSON.parse(localStorage.getItem('tvPlayerNames')) || [];
    } catch (e) {
      return [];
    }
  }

  function saveName(name) {
    const names = [name, ...loadNames().filter((n) => n !== name)].slice(0, 8);
    localStorage.setItem('tvPlayerNames', JSON.stringify(names));
  }

  // ################### Hilfen ###################

  function escapeHtml(text) {
    return String(text).replace(/[&<>"]/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }

  let toastTimer = null;
  function toast(message, info) {
    const box = $('toast');
    box.textContent = message;
    box.className = info ? 'info' : '';
    box.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { box.hidden = true; }, 4000);
  }

  async function call(promise) {
    const result = await promise;
    if (!result.ok) {
      toast(result.error || 'Unbekannter Fehler');
    }
    return result.ok;
  }

  function legend(entries) {
    $('legend').innerHTML = entries
      .map(([glyph, text]) => '<span><span class="glyph">' + glyph + '</span>' + text + '</span>')
      .join('');
  }

  function item(label, focusId, options) {
    const button = document.createElement('button');
    button.className = 'tv-item';
    button.dataset.focusId = focusId;
    button.innerHTML = label;
    Object.assign(button.dataset, options || {});
    return button;
  }

  // Standard-Board ist immer 1 (typisch: ein geteiltes Board) — kein
  // Auto-Hochzählen, die ID lässt sich danach mit ◀▶ anpassen
  const DEFAULT_BOARD_ID = 1;

  // ################### Modal ###################

  function openModal(text, buttons) {
    modal = { buttons, index: 0 };
    $('modalText').textContent = text;
    renderModal();
    $('modal').hidden = false;
  }

  function renderModal() {
    const list = $('modalButtons');
    list.innerHTML = '';
    modal.buttons.forEach((entry, i) => {
      const button = item(escapeHtml(entry.label), 'modal-' + i);
      button.classList.toggle('tv-focus', i === modal.index);
      list.appendChild(button);
    });
  }

  function closeModal() {
    modal = null;
    $('modal').hidden = true;
  }

  function modalHandle(action) {
    if (action === 'up' || action === 'down') {
      const count = modal.buttons.length;
      modal.index = (modal.index + (action === 'down' ? 1 : -1) + count) % count;
      renderModal();
    } else if (action === 'confirm') {
      const selected = modal.buttons[modal.index];
      closeModal();
      if (selected.onSelect) {
        selected.onSelect();
      }
    } else if (action === 'back') {
      closeModal();
    }
  }

  // ################### Screen-Wechsel ###################

  function show(name) {
    screen = name;
    SCREENS.forEach((s) => { $('screen-' + s).hidden = s !== name; });
    Focus.clear();
    render();
  }

  function render() {
    switch (screen) {
      case 'boot': renderBoot(); break;
      case 'menu': renderMenu(); break;
      case 'boards': renderBoards(); break;
      case 'remote': renderRemote(); break;
      case 'lobby': renderLobby(); break;
      case 'settings': renderSettings(); break;
      case 'game': renderGameScreen(); break;
      case 'results': renderResults(); break;
    }
  }

  // ################### BOOT ###################

  function renderBoot() {
    legend([]);
  }

  async function handleBoot() {
    if (appState.broker.configured) {
      leaveBoot();
      return;
    }
    if (!brokerAutoTried) {
      brokerAutoTried = true;
      $('bootMessage').textContent = 'MQTT-Broker wird gestartet…';
      const result = await Api.post('/api/broker', { embedded: true });
      if (!result.ok) {
        $('bootSpinner').hidden = true;
        $('bootMessage').textContent = 'Broker-Start fehlgeschlagen: ' + (result.error || '');
        const list = $('bootRetryList');
        list.innerHTML = '';
        list.appendChild(item('Erneut versuchen', 'retry'));
        Focus.set([...list.children], {
          onConfirm: () => {
            brokerAutoTried = false;
            $('bootSpinner').hidden = false;
            $('bootRetryList').innerHTML = '';
            handleBoot();
          }
        });
        legend([['Ⓐ', 'Auswählen']]);
      }
      // Erfolg: nächstes SSE-Event meldet configured=true und ruft leaveBoot
    }
  }

  function leaveBoot() {
    if (screen !== 'boot') {
      return;
    }
    if (appState.phase === 'RUNNING') {
      wasRunning = true;
      show('game');
    } else if (appState.phase === 'LOBBY') {
      show('lobby');
    } else {
      show('menu');
    }
  }

  // ################### MENU ###################

  function renderMenu() {
    const items = [...$('menuList').children];
    // Board-Verwaltung und Server-Beitritt gibt es nur im Client-/Combined-Modus
    items.find((el) => el.dataset.menu === 'boards').hidden = !appState?.boards;
    items.find((el) => el.dataset.menu === 'remote').hidden = !appState?.client;
    Focus.set(items.filter((el) => !el.hidden), { onConfirm: menuSelect });
    legend([['Ⓐ', 'Auswählen']]);
  }

  async function menuSelect(el) {
    switch (el.dataset.menu) {
      case 'play':
        spectating = false;
        if (appState.phase === 'RUNNING') {
          show('game');
        } else if (appState.phase === 'LOBBY') {
          show('lobby');
        } else if (await call(Api.post('/api/lobby/open'))) {
          show('lobby');
        }
        break;
      case 'boards':
        show('boards');
        break;
      case 'remote':
        show('remote');
        break;
      case 'display':
        spectating = true;
        show('game');
        break;
      case 'quit':
        if (window.dartShell && window.dartShell.quit) {
          window.dartShell.quit();
        } else {
          toast('Bitte das Browserfenster schließen', true);
        }
        break;
    }
  }

  // ################### BOARDS ###################

  function renderBoards() {
    const boards = appState?.boards;
    const list = $('boardsList');
    list.innerHTML = '';
    if (!boards) {
      $('boardsHint').textContent = 'Board-Verwaltung ist im Modus SERVER nicht verfügbar.';
      Focus.set([], {});
      legend([['Ⓑ', 'Zurück']]);
      return;
    }

    list.appendChild(item(boards.scanning ? 'Suche läuft…' : 'Suche starten', 'scan', { action: 'scan' }));

    // verbundene Boards immer oben
    boards.connected.forEach((board) => {
      const badgeClass = board.status === 'CONNECTED' ? 'ok' : 'warn';
      list.appendChild(item(
        '<span>' + escapeHtml(board.name || board.mac)
          + ' <span class="badge ' + badgeClass + '">' + escapeHtml(board.status) + '</span></span>'
          + '<span class="value">Board ' + board.dartboardId + ' · Ⓐ Trennen</span>',
        'conn-' + board.mac, { action: 'disconnect', mac: board.mac }));
    });

    // gefundene Geräte: erst wahrscheinliche Dartboards, dann benannte Geräte
    [...boards.discovered]
      .filter((found) => !boards.connected.some((b) => b.mac === found.mac))
      .sort((a, b) => (b.likelyDartboard ? 1 : 0) - (a.likelyDartboard ? 1 : 0)
        || (b.name ? 1 : 0) - (a.name ? 1 : 0)
        || String(a.name || a.mac).localeCompare(String(b.name || b.mac)))
      .forEach((found) => {
        const chosenId = boardIdChoice[found.mac] || DEFAULT_BOARD_ID;
        const badge = found.likelyDartboard ? ' <span class="badge ok">Wahrscheinlich Dartboard</span>' : '';
        list.appendChild(item(
          '<span>' + escapeHtml(found.name || '(unbenannt)') + badge
            + ' <span class="sub">' + escapeHtml(found.mac) + '</span></span>'
            + '<span class="value">◀ Board ' + chosenId + ' ▶ · Ⓐ Verbinden</span>',
          'found-' + found.mac, { action: 'connect', mac: found.mac }));
      });

    $('boardsHint').textContent = boards.error
      ? 'Fehler: ' + boards.error + ' — Bluetooth in den SteamOS-Einstellungen aktivieren oder externen Connector nutzen.'
      : (boards.scanning ? 'Bluetooth-Suche läuft, gefundene Geräte erscheinen hier…' : '');

    Focus.set([...list.children], {
      onConfirm: boardsSelect,
      onLeft: (el) => boardsStep(el, -1),
      onRight: (el) => boardsStep(el, 1)
    });
    legend([['Ⓐ', 'Auswählen'], ['◀▶', 'Board-ID'], ['Ⓑ', 'Zurück']]);
  }

  function boardsStep(el, delta) {
    if (el.dataset.action !== 'connect') {
      return;
    }
    const mac = el.dataset.mac;
    const current = boardIdChoice[mac] || DEFAULT_BOARD_ID;
    boardIdChoice[mac] = Math.max(1, Math.min(8, current + delta));
    renderBoards();
  }

  async function boardsSelect(el) {
    if (el.dataset.action === 'scan') {
      await call(Api.post('/api/boards/scan'));
    } else if (el.dataset.action === 'connect') {
      const mac = el.dataset.mac;
      await call(Api.post('/api/boards/' + encodeURIComponent(mac) + '/connect',
        { dartboardId: boardIdChoice[mac] || DEFAULT_BOARD_ID }));
    } else if (el.dataset.action === 'disconnect') {
      const mac = el.dataset.mac;
      openModal('Dartboard', [
        { label: 'Neu verbinden', onSelect: () => call(Api.post('/api/boards/' + encodeURIComponent(mac) + '/reconnect')) },
        { label: 'Trennen', onSelect: () => call(Api.post('/api/boards/' + encodeURIComponent(mac) + '/disconnect')) },
        { label: 'Abbrechen' }
      ]);
    }
  }

  // ################### REMOTE (anderem Server beitreten) ###################

  let remoteCfg = loadJson('tvRemote', { host: '', port: 1883, username: 'dartboard', password: 'smartness' });
  let remoteJoinBoardId = DEFAULT_BOARD_ID;

  function saveRemoteCfg() {
    localStorage.setItem('tvRemote', JSON.stringify(remoteCfg));
  }

  function renderRemote() {
    const brokerRemote = appState?.broker?.configured && !appState.broker.embedded;
    const cfg = $('remoteConfig');
    cfg.innerHTML = '';
    cfg.appendChild(item('<span>Server-Adresse</span><span class="value">'
      + escapeHtml(remoteCfg.host || '—') + '</span>', 'r-host', { remote: 'host' }));
    cfg.appendChild(item('<span>Port</span><span class="value">' + remoteCfg.port + '</span>',
      'r-port', { remote: 'port' }));
    cfg.appendChild(item('<span>Benutzer</span><span class="value">'
      + escapeHtml(remoteCfg.username) + '</span>', 'r-user', { remote: 'username' }));
    cfg.appendChild(item('<span>Passwort</span><span class="value">'
      + '•'.repeat(Math.min(8, remoteCfg.password.length)) + '</span>', 'r-pass', { remote: 'password' }));
    cfg.appendChild(item(brokerRemote ? 'Verbunden ✓ — neu verbinden' : 'Mit Server verbinden',
      'r-connect', { remote: 'connect' }));

    const join = $('remoteJoin');
    join.innerHTML = '';
    join.appendChild(item('<span>Eigene Board-ID</span><span class="value">◀ '
      + remoteJoinBoardId + ' ▶</span>', 'r-board', { remote: 'board' }));
    const remote = appState?.client?.remoteLobby;
    const joined = new Set((remote?.players || []).map((p) => p.name));
    loadNames()
      .filter((name) => !joined.has(name))
      .forEach((name) => {
        join.appendChild(item('+ ' + escapeHtml(name), 'r-preset-' + name, { remote: 'preset', name }));
      });
    join.appendChild(item('Neuer Name…', 'r-new', { remote: 'new' }));
    (remote?.players || []).forEach((player) => {
      const badge = player.mine ? ' <span class="badge ok">ich</span>' : '';
      join.appendChild(item('<span>' + escapeHtml(player.name) + badge + '</span>'
        + '<span class="value">Board ' + player.dartboardId + '</span>',
        'r-player-' + player.name,
        { remote: player.mine ? 'leave' : 'info', name: player.name }));
    });

    $('remoteHint').textContent = remote
      ? 'Lobby "' + (remote.serverName || '') + '" (' + remote.phase + ') — der Host startet das '
        + 'Turnier, die Anzeige wechselt dann automatisch.'
      : (brokerRemote
        ? 'Verbunden — warte auf den Lobby-Status des Servers…'
        : 'Adresse des Servers eintragen und verbinden (Standard-Zugang ist vorausgefüllt).');

    Focus.set([...cfg.children, ...join.children], {
      onConfirm: remoteSelect,
      onLeft: (el) => remoteStep(el, -1),
      onRight: (el) => remoteStep(el, 1)
    });
    legend([['Ⓐ', 'Auswählen'], ['◀▶', 'Board-ID'], ['Ⓑ', 'Zurück']]);
  }

  function remoteStep(el, delta) {
    if (el.dataset.remote === 'board') {
      remoteJoinBoardId = Math.max(1, Math.min(8, remoteJoinBoardId + delta));
      renderRemote();
    }
  }

  function remoteEdit(field, title, options) {
    Osk.open(title, (value) => {
      if (value) {
        remoteCfg[field] = value;
        saveRemoteCfg();
      }
      renderRemote();
    }, () => renderRemote(), options);
  }

  async function remoteConnect() {
    if (!remoteCfg.host) {
      toast('Bitte zuerst die Server-Adresse eintragen');
      return;
    }
    if (await call(Api.post('/api/broker', {
      embedded: false,
      host: remoteCfg.host,
      port: remoteCfg.port,
      username: remoteCfg.username,
      password: remoteCfg.password
    }))) {
      await Api.reconnectMqtt();
      toast('Mit Server ' + remoteCfg.host + ' verbunden', true);
    }
  }

  async function remoteJoin(name) {
    if (!name) {
      return;
    }
    if (await call(Api.post('/api/client/join', { playerName: name, dartboardId: remoteJoinBoardId }))) {
      saveName(name);
    }
  }

  function remoteSelect(el) {
    switch (el.dataset.remote) {
      case 'host':
        remoteEdit('host', 'Server-Adresse (IP oder Hostname)',
          { autoCase: false, maxLength: 40, initial: remoteCfg.host });
        break;
      case 'port':
        Osk.open('MQTT-Port', (value) => {
          const port = parseInt(value, 10);
          if (port > 0 && port <= 65535) {
            remoteCfg.port = port;
            saveRemoteCfg();
          } else if (value) {
            toast('Ungültiger Port: ' + value);
          }
          renderRemote();
        }, () => renderRemote(), { autoCase: false, maxLength: 5 });
        break;
      case 'username':
        remoteEdit('username', 'MQTT-Benutzer', { autoCase: false, maxLength: 20, initial: remoteCfg.username });
        break;
      case 'password':
        remoteEdit('password', 'MQTT-Passwort', { autoCase: false, maxLength: 20 });
        break;
      case 'connect':
        remoteConnect();
        break;
      case 'board':
        remoteStep(el, 1);
        break;
      case 'preset':
        remoteJoin(el.dataset.name);
        break;
      case 'new':
        Osk.open('Spielername eingeben', (name) => remoteJoin(name));
        break;
      case 'leave':
        openModal('Spieler "' + el.dataset.name + '" austreten?', [
          { label: 'Austreten', onSelect: () => call(Api.post('/api/client/leave', { playerName: el.dataset.name })) },
          { label: 'Abbrechen' }
        ]);
        break;
    }
  }

  // ################### LOBBY ###################

  function renderLobby() {
    const players = appState?.lobby || [];
    const list = $('lobbyPlayers');
    list.innerHTML = '';
    players.forEach((player) => {
      const local = player.local ? '' : ' <span class="badge">extern</span>';
      list.appendChild(item(
        '<span>' + escapeHtml(player.name) + local + '</span>'
          + '<span class="value">◀ Board ' + player.dartboardId + ' ▶</span>',
        'player-' + player.name, { action: 'player', name: player.name, boardId: player.dartboardId, local: player.local }));
    });

    const add = $('lobbyAdd');
    add.innerHTML = '';
    loadNames()
      .filter((name) => !players.some((p) => p.name === name))
      .forEach((name) => {
        add.appendChild(item('+ ' + escapeHtml(name), 'preset-' + name, { action: 'preset', name }));
      });
    add.appendChild(item('Neuer Name…', 'new', { action: 'new' }));
    const start = item('Weiter zu den Spieleinstellungen', 'continue', { action: 'continue' });
    if (players.length === 0) {
      start.classList.add('disabled');
      start.disabled = true;
    }
    add.appendChild(start);

    Focus.set([...list.children, ...add.children], {
      onConfirm: lobbySelect,
      onLeft: (el) => lobbyStep(el, -1),
      onRight: (el) => lobbyStep(el, 1),
      onAction: (action, el) => {
        if (!el) {
          return;
        }
        if (action === 'action-y') {
          if (el.dataset.action === 'player') {
            removePlayer(el.dataset.name);
          } else if (el.dataset.action === 'preset') {
            removeSavedName(el.dataset.name);
          }
        } else if ((action === 'lb' || action === 'rb') && el.dataset.action === 'player') {
          movePlayer(el.dataset.name, action === 'lb' ? -1 : 1);
        }
      }
    });
    legend([['Ⓐ', 'Auswählen'], ['◀▶', 'Board-ID'], ['LB/RB', 'Reihenfolge'],
      ['Ⓨ', 'Entfernen'], ['Ⓑ', 'Lobby schließen']]);
  }

  async function movePlayer(name, offset) {
    await call(Api.post('/api/lobby/players/' + encodeURIComponent(name) + '/move', { offset }));
  }

  function removeSavedName(name) {
    localStorage.setItem('tvPlayerNames',
      JSON.stringify(loadNames().filter((n) => n !== name)));
    toast('Name "' + name + '" aus den Vorschlägen entfernt', true);
    renderLobby();
  }

  async function addPlayer(name) {
    if (!name) {
      return;
    }
    if (await call(Api.post('/api/lobby/players', { name, dartboardId: DEFAULT_BOARD_ID }))) {
      saveName(name);
    }
  }

  function removePlayer(name) {
    openModal('Spieler "' + name + '" entfernen?', [
      { label: 'Entfernen', onSelect: () => call(Api.del('/api/lobby/players/' + encodeURIComponent(name))) },
      { label: 'Abbrechen' }
    ]);
  }

  async function lobbyStep(el, delta) {
    if (el.dataset.action !== 'player' || el.dataset.local !== 'true') {
      return;
    }
    const boardId = Math.max(1, Math.min(8, Number(el.dataset.boardId) + delta));
    // erneutes Beitreten mit gleichem Namen aktualisiert die Board-ID
    await call(Api.post('/api/lobby/players', { name: el.dataset.name, dartboardId: boardId }));
  }

  function lobbySelect(el) {
    switch (el.dataset.action) {
      case 'preset':
        addPlayer(el.dataset.name);
        break;
      case 'new':
        Osk.open('Spielername eingeben', (name) => addPlayer(name));
        break;
      case 'continue':
        show('settings');
        break;
      case 'player':
        openModal('Spieler "' + el.dataset.name + '"', [
          { label: 'Nach oben', onSelect: () => movePlayer(el.dataset.name, -1) },
          { label: 'Nach unten', onSelect: () => movePlayer(el.dataset.name, 1) },
          { label: 'Entfernen', onSelect: () => call(Api.del('/api/lobby/players/' + encodeURIComponent(el.dataset.name))) },
          { label: 'Abbrechen' }
        ]);
        break;
    }
  }

  function lobbyBack() {
    openModal('Lobby schließen?', [
      {
        label: 'Lobby schließen',
        onSelect: async () => {
          await call(Api.post('/api/lobby/close'));
          show('menu');
        }
      },
      { label: 'In der Lobby bleiben' }
    ]);
  }

  // ################### SETTINGS ###################

  const START_SCORES = [301, 501, 701];

  function settingsRows() {
    const x01 = settings.gameMode === 'x01';
    const rows = [
      { key: 'gameMode', label: 'Spielmodus', value: x01 ? 'X01' : 'Cricket' }
    ];
    if (x01) {
      rows.push({ key: 'startScore', label: 'Startpunkte', value: settings.startScore });
      rows.push({ key: 'doubleIn', label: 'Double-In', value: settings.doubleIn ? 'An' : 'Aus' });
      rows.push({ key: 'doubleOut', label: 'Double-Out', value: settings.doubleOut ? 'An' : 'Aus' });
    }
    rows.push({ key: 'games', label: 'Anzahl Spiele', value: settings.games });
    rows.push({ key: 'penaltyCostCents', label: 'Kosten pro Strafpunkt', value: settings.penaltyCostCents + ' Cent' });
    rows.push({ key: 'schnapszahlPenalty', label: 'Hausregel: Schnapszahl-Strafe', value: settings.houseRules.schnapszahlPenalty ? 'An' : 'Aus' });
    rows.push({ key: 'wallHitPenalty', label: 'Hausregel: Wandtreffer-Strafe', value: settings.houseRules.wallHitPenalty ? 'An' : 'Aus' });
    rows.push({ key: 'placementPenalty', label: 'Hausregel: Platzierungs-Strafe', value: settings.houseRules.placementPenalty ? 'An' : 'Aus' });
    return rows;
  }

  function renderSettings() {
    const list = $('settingsList');
    list.innerHTML = '';
    settingsRows().forEach((row) => {
      list.appendChild(item(
        '<span>' + row.label + '</span><span class="value">◀ ' + row.value + ' ▶</span>',
        'set-' + row.key, { key: row.key }));
    });
    list.appendChild(item('Turnier starten', 'start', { key: 'start' }));

    Focus.set([...list.children], {
      onConfirm: settingsSelect,
      onLeft: (el) => settingsStep(el, -1),
      onRight: (el) => settingsStep(el, 1)
    });
    legend([['◀▶', 'Ändern'], ['Ⓐ', 'Umschalten / Starten'], ['Ⓑ', 'Zurück zur Lobby']]);
  }

  function cycle(values, current, delta) {
    const index = values.indexOf(current);
    return values[(index + delta + values.length) % values.length];
  }

  function settingsStep(el, delta) {
    const key = el.dataset.key;
    switch (key) {
      case 'gameMode':
        settings.gameMode = settings.gameMode === 'x01' ? 'cricket' : 'x01';
        break;
      case 'startScore':
        settings.startScore = cycle(START_SCORES, settings.startScore, delta);
        break;
      case 'doubleIn':
      case 'doubleOut':
        settings[key] = !settings[key];
        break;
      case 'games':
        settings.games = Math.max(1, Math.min(9, settings.games + delta));
        break;
      case 'penaltyCostCents':
        settings.penaltyCostCents = Math.max(0, Math.min(500, settings.penaltyCostCents + delta * 10));
        break;
      case 'schnapszahlPenalty':
      case 'wallHitPenalty':
      case 'placementPenalty':
        settings.houseRules[key] = !settings.houseRules[key];
        break;
      default:
        return;
    }
    saveSettings();
    renderSettings();
  }

  async function settingsSelect(el) {
    if (el.dataset.key === 'start') {
      spectating = false;
      await call(Api.post('/api/tournament/start', settings));
      // Erfolg: SSE meldet phase=RUNNING und wechselt zur Spielanzeige
    } else {
      settingsStep(el, 1);
    }
  }

  // ################### GAME (Anzeige über MQTT) ###################

  const CRICKET_NUMBERS = [15, 16, 17, 18, 19, 20, 25];

  function renderGameScreen() {
    const running = lastGame && lastGame.gameState !== 'UNDEFINED';
    $('gameArea').hidden = !running;
    $('gameLobbyWait').hidden = running;
    if (!running) {
      const players = appState?.lobby || [];
      $('gameLobbyTitle').textContent = appState?.phase === 'LOBBY'
        ? 'Lobby "' + (appState.serverName || '') + '" – wartet auf Turnierstart'
        : 'Kein Spiel aktiv';
      const list = $('gameLobbyPlayers');
      list.innerHTML = '';
      players.forEach((player) => {
        const li = document.createElement('li');
        li.textContent = player.name + ' (Board ' + player.dartboardId + ')';
        list.appendChild(li);
      });
    } else {
      renderGallery(lastGame);
      renderCricket(lastGame);
      renderGameMeta(lastGame);
    }
    // Aktionsleiste ist fokussierbar/klickbar, damit auch Maus/Tastatur-
    // emulierte Controller (Steam-Desktop-Konfiguration) das Spiel bedienen
    const nextButton = document.querySelector('#gameButtons [data-game="next"]');
    nextButton.classList.toggle('attention', !!lastGame && lastGame.gameState === 'WAITING');
    Focus.set([...$('gameButtons').children], {
      onConfirm: (el) => gameAction(el.dataset.game),
      onLeft: () => Focus.handle('up'),
      onRight: () => Focus.handle('down')
    });
    legend([
      ['◀▶/Ⓐ', 'Aktion wählen'], ['Ⓧ', 'Daneben'], ['Ⓨ', 'Wandtreffer'], ['☰/F', 'Spielmenü']
    ]);
  }

  function renderGameMeta(data) {
    // Board-ID sichtbar machen: Würfe zählen nur vom Board des aktuellen
    // Spielers — eine falsche Zuordnung fällt sonst nicht auf
    $('gameMeta').textContent = 'Spiel ' + data.spielId + ' von ' + data.anzahlSpiele
      + ' · Status: ' + data.gameState
      + ' · Am Zug: Board ' + (data.currentPlayer ? data.currentPlayer.dartboardId : '?')
      + ' · Strafpunkt: ' + data.kostenStrafpunkte + ' Cent';
    const placement = $('gamePlacement');
    if (data.spielerPlatzierung && data.spielerPlatzierung.length > 0) {
      placement.textContent = 'Platzierung: '
        + data.spielerPlatzierung.map((p, i) => (i + 1) + '. ' + p.name).join('  ·  ');
    } else {
      placement.textContent = '';
    }
  }

  /* Kachel-Galerie — Logik aus dem Display-UI übernommen (web/display) */
  function renderGallery(data) {
    const viewport = $('galleryViewport');
    const track = $('galleryTrack');
    const players = data.spielerReihenfolge;
    const current = data.currentPlayer;
    if (!players || players.length === 0) {
      viewport.style.display = 'none';
      return;
    }
    viewport.style.display = 'block';

    const lineup = players.map((spieler) => spieler.id).join(',');
    if (track.dataset.lineup !== lineup) {
      track.dataset.lineup = lineup;
      track.innerHTML = '';
      players.forEach((spieler) => {
        const tile = document.createElement('div');
        tile.className = 'tile';
        tile.id = 'tile-' + spieler.id;
        tile.innerHTML =
          '<div class="tile-name"></div>' +
          '<div class="tile-score"></div>' +
          '<div class="tile-detail tile-points"></div>' +
          '<div class="tile-detail tile-lastthrow"></div>' +
          '<div class="tile-detail tile-turn"></div>' +
          '<div class="tile-detail tile-boxes"></div>';
        track.appendChild(tile);
      });
    }

    const cricket = data.gameMode === 'CRICKET';
    players.forEach((spieler) => {
      const tile = $('tile-' + spieler.id);
      const active = spieler.id === current.id;
      tile.classList.toggle('active', active);
      tile.classList.toggle('waiting', active && data.gameState === 'WAITING');
      tile.querySelector('.tile-name').innerText = spieler.name;
      tile.querySelector('.tile-score').innerText = spieler.punktestand;
      if (cricket && active) {
        const own = cricketScoringNumbers(data, players, spieler.name);
        const others = cricketThreatNumbers(data, players, spieler.name);
        tile.querySelector('.tile-points').innerHTML =
          '<span class="cricket-own">' + (own.length ? own.join(' ') : '–') + '</span>' +
          '<span class="cricket-others">' + (others.length ? others.join(' ') : '–') + '</span>';
      } else {
        tile.querySelector('.tile-points').innerHTML = '';
      }
      if (active) {
        tile.querySelector('.tile-lastthrow').innerText = 'Wurf: ' + data.letzterWurf;
        tile.querySelector('.tile-turn').innerText = 'Zug: ' + data.punkteSpielzug;
        const frei = data.gameState === 'RUNNING' ? current.freieWuerfe : 0;
        const geworfen = Math.min(3, Math.max(0, 3 - frei));
        let boxes = '';
        for (let i = 0; i < 3; i++) {
          boxes += i < geworfen ? '[x]' : '[ ]';
        }
        tile.querySelector('.tile-boxes').innerText = boxes;
      }
    });

    const vw = window.innerWidth / 100;
    const index = players.findIndex((spieler) => spieler.id === current.id);
    const offset = index * (15 + 1.5) * vw + 15 * vw;
    track.style.transform = 'translateX(' + (viewport.clientWidth / 2 - offset) + 'px)';
  }

  function cricketLabel(number) {
    return number === 25 ? 'Bull' : String(number);
  }

  function cricketMarks(data, name, number) {
    return Number((data.modeData || {})['marks:' + name + ':' + number] || 0);
  }

  function cricketScoringNumbers(data, players, name) {
    return CRICKET_NUMBERS
      .filter((number) => cricketMarks(data, name, number) >= 3
        && players.some((spieler) =>
          spieler.name !== name && cricketMarks(data, spieler.name, number) < 3))
      .map(cricketLabel);
  }

  function cricketThreatNumbers(data, players, name) {
    return CRICKET_NUMBERS
      .filter((number) => cricketMarks(data, name, number) < 3
        && players.some((spieler) =>
          spieler.name !== name && cricketMarks(data, spieler.name, number) >= 3))
      .map(cricketLabel);
  }

  function renderCricket(data) {
    const board = $('cricketBoard');
    const players = data.spielerReihenfolge;
    if (data.gameMode !== 'CRICKET' || !players || players.length === 0) {
      board.hidden = true;
      return;
    }
    board.hidden = false;

    const deadNumbers = new Set(CRICKET_NUMBERS.filter((number) =>
      players.every((spieler) => cricketMarks(data, spieler.name, number) >= 3)));
    const cell = (name, number) => {
      if (deadNumbers.has(number)) {
        return { symbol: '/', closed: true };
      }
      const marks = Math.min(3, cricketMarks(data, name, number));
      return { symbol: '⊗'.repeat(marks), closed: marks >= 3 };
    };
    const cls = (spieler, extra) => {
      const classes = [];
      if (extra) classes.push(extra);
      if (spieler.id === data.currentPlayer.id) classes.push('active-player');
      return classes.length ? ' class="' + classes.join(' ') + '"' : '';
    };

    let html = '<tr><th></th>'
      + CRICKET_NUMBERS.map((number) => '<th>' + cricketLabel(number) + '</th>').join('')
      + '<th>Punkte</th></tr>';
    players.forEach((spieler) => {
      html += '<tr><th' + cls(spieler) + '>' + escapeHtml(spieler.name) + '</th>'
        + CRICKET_NUMBERS.map((number) => {
          const c = cell(spieler.name, number);
          return '<td' + cls(spieler, c.closed ? 'closed' : '') + '>' + c.symbol + '</td>';
        }).join('')
        + '<td' + cls(spieler) + '>' + spieler.punktestand + '</td></tr>';
    });
    $('cricketTable').innerHTML = html;
  }

  function gameAction(kind) {
    const boardId = lastGame?.currentPlayer?.dartboardId;
    switch (kind) {
      case 'next':
        // NEXT nur am Zugende — verhindert versehentliche Spielerwechsel
        if (lastGame && lastGame.gameState !== 'RUNNING') {
          Api.publishControl('999', boardId);
        } else {
          toast('NEXT erst am Ende des Spielzugs', true);
        }
        break;
      case 'miss':
        Api.publishControl('998', boardId);
        showGif('randgif', 3500);
        break;
      case 'wall':
        Api.publishControl('997', boardId);
        break;
      case 'menu': {
        const buttons = [{ label: 'Weiter spielen' }];
        if ((appState?.boards?.connected || []).length > 0) {
          buttons.push({ label: 'Dartboard neu verbinden', onSelect: reconnectBoards });
        }
        buttons.push({ label: 'Board zurücksetzen (Bounce Out)', onSelect: () => Api.publishControl('996', boardId) });
        buttons.push({
          label: 'Turnier abbrechen',
          onSelect: () => openModal('Turnier wirklich abbrechen?', [
            { label: 'Ja, abbrechen', onSelect: () => call(Api.post('/api/tournament/abort')) },
            { label: 'Nein' }
          ])
        });
        buttons.push({ label: 'Zum Hauptmenü', onSelect: () => show('menu') });
        openModal('Spielmenü', buttons);
        break;
      }
    }
  }

  /* Zombie-Verbindung („verbunden", aber keine Würfe): Verbindung zu allen
     Boards abreißen und neu aufbauen, ohne das Spiel zu unterbrechen */
  async function reconnectBoards() {
    for (const board of appState?.boards?.connected || []) {
      await call(Api.post('/api/boards/' + encodeURIComponent(board.mac) + '/reconnect'));
    }
    toast('Dartboard wird neu verbunden…', true);
  }

  function gameHandle(action) {
    switch (action) {
      case 'action-x':
        gameAction('miss');
        break;
      case 'action-y':
        gameAction('wall');
        break;
      case 'menu':
        gameAction('menu');
        break;
      case 'back':
        // absichtlich ohne Funktion: kein versehentliches Verlassen
        break;
      default:
        // Richtungen + Bestätigen navigieren die Aktionsleiste
        Focus.handle(action);
    }
  }

  // ################### Sounds + GIFs (Assets vom Display-UI) ###################

  const SOUND_EVENTS = {
    WINNER: { sound: 'winner.wav', gif: 'winnergif', gifMs: 5500 },
    TREFFER: { sound: 'treffer.wav' },
    DOUBLE: { sound: 'double.wav' },
    TRIPLE: { sound: 'triple.wav' },
    BULLSEYE: { sound: 'bullseye.wav', gif: 'bullgif', gifMs: 3000 },
    RESET: { sound: 'reset.wav' },
    SPIELSTART: { sound: 'spielstart.wav' },
    STRAFE: { sound: 'strafe.wav', gif: 'strafegif', gifMs: 4500 },
    UEBERWORFEN: { sound: 'ueberworfen.wav', gif: 'ueberworfengif', gifMs: 2700 },
    MAXPOINTS: { sound: '180.wav', gif: 'maxPointsgif', gifMs: 4500 }
  };

  function showGif(id, ms) {
    $(id).hidden = false;
    setTimeout(() => { $(id).hidden = true; }, ms);
  }

  function handleSound(name) {
    const event = SOUND_EVENTS[name];
    if (!event) {
      return;
    }
    if (event.gif) {
      showGif(event.gif, event.gifMs);
    }
    new Audio('/display/sounds/' + event.sound).play().catch(() => { });
  }

  // ################### RESULTS ###################

  function renderResults() {
    const box = $('resultsRanking');
    const games = appState?.lastResults || [];
    if (games.length === 0) {
      box.innerHTML = '<p class="hint">Keine Ergebnisse vorhanden.</p>';
    } else {
      box.innerHTML = games.map((ranking, i) =>
        '<h2>Spiel ' + (i + 1) + '</h2><ol>'
        + ranking.map((name) => '<li>' + escapeHtml(name) + '</li>').join('')
        + '</ol>').join('');
    }

    const list = $('resultsList');
    list.innerHTML = '';
    list.appendChild(item('Nochmal spielen', 'again', { action: 'again' }));
    list.appendChild(item('Zur Lobby', 'lobby', { action: 'lobby' }));
    list.appendChild(item('Hauptmenü', 'menu', { action: 'menu' }));

    Focus.set([...list.children], { onConfirm: resultsSelect });
    legend([['Ⓐ', 'Auswählen']]);
  }

  async function resultsSelect(el) {
    const openLobbyIfNeeded = async () =>
      appState.phase === 'LOBBY' || await call(Api.post('/api/lobby/open'));
    switch (el.dataset.action) {
      case 'again':
        if (await openLobbyIfNeeded()) {
          show('settings');
        }
        break;
      case 'lobby':
        if (await openLobbyIfNeeded()) {
          show('lobby');
        }
        break;
      case 'menu':
        show('menu');
        break;
    }
  }

  // ################### SSE-Zustand + erzwungene Wechsel ###################

  function updateStatusBar() {
    $('statusServer').textContent = appState.serverName || '';
    $('statusBroker').innerHTML = appState.broker.configured
      ? '<span class="ok">Broker ✓</span>'
      : '<span class="warn">Broker fehlt</span>';
    const boards = appState.boards;
    $('statusBoards').textContent = boards
      ? 'Boards: ' + boards.connected.length
      : '';
    $('statusPhase').textContent = 'Phase: ' + appState.phase;
  }

  async function handleState(state) {
    const previousPhase = appState?.phase;
    appState = state;
    updateStatusBar();

    if (state.broker.configured && !mqttStarted) {
      mqttStarted = true;
      await Api.refreshConfig();
      Api.connectMqtt();
    }

    if (screen === 'boot') {
      handleBoot();
      return;
    }

    // Turnierstart/-ende erzwingen Screenwechsel
    if (state.phase === 'RUNNING' && previousPhase !== 'RUNNING') {
      wasRunning = true;
      if (screen !== 'game') {
        show('game');
        return;
      }
    }
    if (screen === 'game' && wasRunning && !spectating && state.phase !== 'RUNNING') {
      wasRunning = false;
      show('results');
      return;
    }

    // aktuellen Screen mit frischem Zustand neu zeichnen
    if (screen !== 'game' || $('gameArea').hidden) {
      render();
    }
  }

  // ################### Eingabe-Routing ###################

  document.addEventListener('tv-action', (event) => {
    const action = event.detail.action;
    if (Osk.isOpen()) {
      Osk.handleAction(action);
      return;
    }
    if (modal) {
      modalHandle(action);
      return;
    }
    if (screen === 'game') {
      gameHandle(action);
      return;
    }
    if (action === 'back') {
      switch (screen) {
        case 'boards': show('menu'); return;
        case 'remote': show('menu'); return;
        case 'lobby': lobbyBack(); return;
        case 'settings': show('lobby'); return;
        case 'results': show('menu'); return;
      }
      return;
    }
    Focus.handle(action);
  });

  /* Maus-/Touch-Klicks (auch Controller, die Steam als Maus emuliert):
     Klick auf ein fokussierbares Element wirkt wie Fokussieren + Ⓐ.
     OSK-Tasten behandelt osk.js selbst. */
  document.addEventListener('click', (event) => {
    const el = event.target.closest('.tv-item');
    if (!el || Osk.isOpen()) {
      return;
    }
    if (modal) {
      const buttons = [...$('modalButtons').children];
      const found = buttons.indexOf(el);
      if (found >= 0) {
        modal.index = found;
        modalHandle('confirm');
      }
      return;
    }
    Focus.activate(el);
  });

  // ################### Start ###################

  Api.onGameUpdate((data) => {
    lastGame = data;
    if (screen === 'game') {
      renderGameScreen();
    } else if (screen === 'remote' && data.gameState === 'RUNNING') {
      // der entfernte Server hat das Turnier gestartet
      spectating = true;
      show('game');
    }
  });
  Api.onSound(handleSound);
  Api.onState(handleState);
  TvInput.start();
})();
