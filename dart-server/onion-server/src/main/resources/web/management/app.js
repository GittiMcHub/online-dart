/* Management-UI: rendert den Zustand aus /api/events (SSE) und schickt
 * Kommandos an die REST-API. Kein Framework, kein Build-Step. */

const $ = (id) => document.getElementById(id);

async function post(path, body) {
    const response = await fetch(path, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: body === undefined ? null : JSON.stringify(body),
    });
    return response.json();
}

async function del(path) {
    const response = await fetch(path, {method: 'DELETE'});
    return response.json();
}

function setStatus(id, result) {
    const el = $(id);
    if (result.ok) {
        el.textContent = '✓';
        el.className = 'status ok';
    } else {
        el.textContent = result.error || 'Fehler';
        el.className = 'status error';
    }
    setTimeout(() => { el.textContent = ''; }, 5000);
}

/* ---------- Broker ---------- */

document.querySelectorAll('input[name="brokerType"]').forEach((radio) => {
    radio.addEventListener('change', () => {
        const embedded = radio.value === 'embedded';
        $('embeddedFields').hidden = !embedded;
        $('externalFields').hidden = embedded;
    });
});

$('brokerButton').addEventListener('click', async () => {
    const embedded = document.querySelector('input[name="brokerType"]:checked').value === 'embedded';
    const body = embedded
        ? {embedded: true, port: Number($('brokerPort').value), wsPort: Number($('brokerWsPort').value)}
        : {
            embedded: false,
            host: $('brokerHost').value,
            port: Number($('brokerExtPort').value),
            username: $('brokerUser').value,
            password: $('brokerPassword').value,
        };
    setStatus('brokerStatus', await post('/api/broker', body));
});

/* ---------- Lobby ---------- */

$('lobbyOpenButton').addEventListener('click', async () => {
    setStatus('brokerStatus', await post('/api/lobby/open'));
});

$('lobbyCloseButton').addEventListener('click', async () => {
    setStatus('brokerStatus', await post('/api/lobby/close'));
});

$('addPlayerForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const result = await post('/api/lobby/players', {
        name: $('playerName').value,
        dartboardId: Number($('playerBoardId').value),
    });
    if (result.ok) {
        $('playerName').value = '';
        $('playerName').focus();
    }
    setStatus('tournamentStatus', result);
});

async function removePlayer(name) {
    setStatus('tournamentStatus', await del('/api/lobby/players/' + encodeURIComponent(name)));
}

/* ---------- Dartboards (BLE) ---------- */

$('scanButton').addEventListener('click', async () => {
    setStatus('scanStatus', await post('/api/boards/scan'));
});

async function connectBoard(mac) {
    const dartboardId = prompt('Dartboard-ID für ' + mac + ':', '1');
    if (dartboardId === null) {
        return;
    }
    setStatus('scanStatus', await post('/api/boards/' + encodeURIComponent(mac) + '/connect',
        {dartboardId: Number(dartboardId)}));
}

async function disconnectBoard(mac) {
    setStatus('scanStatus', await post('/api/boards/' + encodeURIComponent(mac) + '/disconnect'));
}

function renderBoards(state) {
    const showBoards = state.mode !== 'SERVER';
    $('boardsSection').hidden = !showBoards;
    if (!showBoards || !state.boards) {
        return;
    }
    const boards = state.boards;
    $('scanButton').disabled = boards.scanning;
    $('scanButton').textContent = boards.scanning ? 'Suche läuft …' : 'Nach Dartboards suchen';
    if (boards.error) {
        $('scanStatus').textContent = boards.error;
        $('scanStatus').className = 'status error';
    }

    const connectedMacs = new Set(boards.connected.map((b) => b.mac));
    const discovered = $('discoveredList');
    discovered.innerHTML = '';
    boards.discovered
        .filter((board) => !connectedMacs.has(board.mac))
        .forEach((board) => {
            const li = document.createElement('li');
            const label = document.createElement('span');
            label.textContent = (board.name || '(unbenannt)') + ' – ' + board.mac
                + (board.likelyDartboard ? ' 🎯' : '');
            li.appendChild(label);
            const connect = document.createElement('button');
            connect.textContent = 'Verbinden';
            connect.addEventListener('click', () => connectBoard(board.mac));
            li.appendChild(connect);
            discovered.appendChild(li);
        });

    const connected = $('connectedList');
    connected.innerHTML = '';
    boards.connected.forEach((board) => {
        const li = document.createElement('li');
        const label = document.createElement('span');
        label.textContent = (board.name || board.mac) + ' → dartboard/' + board.dartboardId
            + ' [' + ({
                CONNECTING: 'verbinde …',
                CONNECTED: 'verbunden',
                RECONNECTING: 'verbinde neu …',
                DISCONNECTED: 'getrennt',
            }[board.status] || board.status) + ']';
        li.appendChild(label);
        const disconnect = document.createElement('button');
        disconnect.textContent = 'Trennen';
        disconnect.addEventListener('click', () => disconnectBoard(board.mac));
        li.appendChild(disconnect);
        connected.appendChild(li);
    });
}

/* ---------- Client (Lobby beitreten) ---------- */

$('clientJoinForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const result = await post('/api/client/join', {
        playerName: $('clientPlayerName').value,
        dartboardId: Number($('clientBoardId').value),
    });
    setStatus('clientStatus', result);
});

async function clientLeave(name) {
    setStatus('clientStatus', await post('/api/client/leave', {playerName: name}));
}

/* ---------- Turnier ---------- */

$('gameMode').addEventListener('change', () => {
    const x01 = $('gameMode').value === 'x01';
    $('startScoreLabel').hidden = !x01;
    $('x01Options').hidden = !x01;
});

$('startButton').addEventListener('click', async () => {
    setStatus('tournamentStatus', await post('/api/tournament/start', {
        gameMode: $('gameMode').value,
        startScore: Number($('startScore').value),
        doubleIn: $('doubleIn').checked,
        doubleOut: $('doubleOut').checked,
        games: Number($('games').value),
        penaltyCostCents: Number($('penaltyCost').value),
        houseRules: {
            schnapszahlPenalty: $('schnapszahlPenalty').checked,
            wallHitPenalty: $('wallHitPenalty').checked,
            placementPenalty: $('placementPenalty').checked,
        },
    }));
});

$('abortButton').addEventListener('click', async () => {
    if (confirm('Laufendes Turnier wirklich abbrechen?')) {
        setStatus('tournamentStatus', await post('/api/tournament/abort'));
    }
});

/* ---------- Rendering ---------- */

function renderClient(state) {
    const isClient = state.mode === 'CLIENT';
    // pure clients join remotely; COMBINED hosts add players locally instead
    $('clientSection').hidden = !isClient;
    $('lobbySection').hidden = isClient;
    $('tournamentSection').hidden = isClient;
    if (!isClient || !state.client) {
        return;
    }
    const remote = state.client.remoteLobby;
    if (!remote) {
        $('remoteLobbyInfo').textContent = 'Noch keine Lobby-Informationen vom Server.';
        $('remotePlayerList').innerHTML = '';
        return;
    }
    $('remoteLobbyInfo').textContent = 'Server "' + remote.serverName + '" – ' + ({
        IDLE: 'Lobby geschlossen',
        LOBBY: 'Lobby geöffnet, Beitritt möglich',
        RUNNING: 'Turnier läuft',
    }[remote.phase] || remote.phase);
    const list = $('remotePlayerList');
    list.innerHTML = '';
    remote.players.forEach((player) => {
        const li = document.createElement('li');
        const label = document.createElement('span');
        label.textContent = player.name + ' – Board ' + player.dartboardId + (player.mine ? ' (dieser Client)' : '');
        li.appendChild(label);
        if (player.mine && remote.phase === 'LOBBY') {
            const leave = document.createElement('button');
            leave.textContent = 'Verlassen';
            leave.addEventListener('click', () => clientLeave(player.name));
            li.appendChild(leave);
        }
        list.appendChild(li);
    });
}

function render(state) {
    renderClient(state);
    renderBoards(state);
    $('modeBadge').textContent = 'Modus: ' + state.mode;
    $('phaseBadge').textContent = {
        IDLE: 'Bereit',
        LOBBY: 'Lobby geöffnet',
        RUNNING: 'Turnier läuft',
    }[state.phase] || state.phase;
    $('phaseBadge').className = 'badge ' + ({IDLE: '', LOBBY: 'blue', RUNNING: 'green'}[state.phase] || '');

    const broker = state.broker;
    $('brokerBadge').textContent = broker.configured
        ? (broker.connected ? 'MQTT verbunden' : 'MQTT getrennt')
        : 'Kein Broker';
    $('brokerBadge').className = 'badge ' + (broker.connected ? 'green' : 'orange');
    $('brokerButton').textContent = broker.configured ? 'Broker neu konfigurieren' : 'Broker starten';

    const inLobby = state.phase === 'LOBBY';
    const running = state.phase === 'RUNNING';
    $('lobbyOpenButton').hidden = inLobby || running;
    $('lobbyOpenButton').disabled = !broker.configured;
    $('lobbyCloseButton').hidden = !inLobby;
    $('lobbyContent').hidden = !inLobby && state.lobby.length === 0;

    const list = $('playerList');
    list.innerHTML = '';
    state.lobby.forEach((player) => {
        const li = document.createElement('li');
        const label = document.createElement('span');
        label.textContent = player.name + ' – Board ' + player.dartboardId;
        li.appendChild(label);
        if (!player.local) {
            const remote = document.createElement('span');
            remote.className = 'remote';
            remote.textContent = '(remote)';
            li.appendChild(remote);
        }
        if (inLobby) {
            const remove = document.createElement('button');
            remove.textContent = 'Entfernen';
            remove.addEventListener('click', () => removePlayer(player.name));
            li.appendChild(remove);
        }
        list.appendChild(li);
    });

    $('startButton').hidden = running;
    $('startButton').disabled = !inLobby || state.lobby.length === 0;
    $('abortButton').hidden = !running;

    $('currentGame').hidden = !running;
    if (running && state.game && state.game.gameState) {
        const game = state.game;
        const current = game.currentPlayer ? game.currentPlayer.name : '-';
        $('gameSummary').textContent = 'Spiel ' + game.spielId + ' von ' + game.anzahlSpiele
            + ' – am Board: ' + current
            + (game.gameState === 'WAITING' ? ' (wartet auf Wechsel)' : '');
    }

    $('results').hidden = state.lastResults.length === 0;
    const results = $('resultsList');
    results.innerHTML = '';
    state.lastResults.forEach((ranking, index) => {
        const li = document.createElement('li');
        li.textContent = 'Spiel ' + (index + 1) + ': ' + ranking.join(', ');
        results.appendChild(li);
    });
}

/* ---------- SSE ---------- */

function connectEvents() {
    const source = new EventSource('/api/events');
    source.addEventListener('state', (event) => render(JSON.parse(event.data)));
    source.onerror = () => {
        source.close();
        setTimeout(connectEvents, 2000);
    };
}

connectEvents();
