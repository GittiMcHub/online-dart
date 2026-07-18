/* Zwei Kanäle wie beim Display-UI:
   - SSE /api/events treibt App-Zustand (Lobby, Boards, Phase, Einstellungen)
   - MQTT über WebSocket treibt die Spielanzeige (gameUpdate + playSound)
   Steuer-Codes (999 NEXT, 998 Rand, 997 Wand, 996 Reset) gehen per MQTT auf
   das Dartboard-Topic — identisch zum Display-UI. */
const Api = (() => {

  let stateCallback = null;
  let gameUpdateCallback = null;
  let soundCallback = null;
  let lobbyStateCallback = null;
  let mqttClient = null;

  async function post(path, body) {
    try {
      const response = await fetch(path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {})
      });
      return await response.json();
    } catch (error) {
      return { ok: false, error: 'Server nicht erreichbar: ' + error.message };
    }
  }

  async function del(path) {
    try {
      const response = await fetch(path, { method: 'DELETE' });
      return await response.json();
    } catch (error) {
      return { ok: false, error: 'Server nicht erreichbar: ' + error.message };
    }
  }

  function startSse() {
    const source = new EventSource('/api/events');
    source.addEventListener('state', (event) => {
      if (stateCallback) {
        stateCallback(JSON.parse(event.data));
      }
    });
    // EventSource verbindet selbstständig neu; kein weiteres Handling nötig
  }

  /* /config.js wird beim Seitenstart geladen; wird der Broker erst danach
     konfiguriert, muss die Datei neu geladen und ausgeführt werden, damit
     window.DART_CONFIG aktuell ist (wsHost ist ein location-Ausdruck). */
  async function refreshConfig() {
    const response = await fetch('/config.js', { cache: 'no-cache' });
    new Function(await response.text())();
    return window.DART_CONFIG;
  }

  function connectMqtt() {
    if (mqttClient) {
      return;
    }
    const cfg = window.DART_CONFIG;
    if (!cfg || !cfg.configured) {
      return;
    }
    const brokerUrl = 'ws://' + cfg.wsHost + ':' + cfg.wsPort + '/';
    mqttClient = mqtt.connect(brokerUrl, {
      username: cfg.username,
      password: cfg.password,
      clientId: 'dart-tv-' + Math.random().toString(16).slice(2, 8)
    });
    mqttClient.on('connect', () => {
      mqttClient.subscribe('status/gameUpdate', { qos: 2 });
      mqttClient.subscribe('status/playSound', { qos: 2 });
      mqttClient.subscribe('lobby/state', { qos: 1 });
    });
    mqttClient.on('message', (topic, message) => {
      const data = JSON.parse(message.toString());
      if (topic === 'status/gameUpdate' && gameUpdateCallback) {
        gameUpdateCallback(data);
      } else if (topic === 'status/playSound' && soundCallback) {
        soundCallback(data.sound);
      } else if (topic === 'lobby/state' && lobbyStateCallback) {
        lobbyStateCallback(data);
      }
    });
  }

  /* Der Server wertet Würfe nur vom Board des aktuellen Spielers — die
     Steuer-Codes müssen deshalb auf dessen Topic gehen (boardId aus dem
     letzten gameUpdate), sonst auf das Standard-Topic. */
  function publishControl(code, boardId) {
    if (!mqttClient) {
      return false;
    }
    const topic = boardId ? 'dartboard/' + boardId : window.DART_CONFIG.defaultDartboardTopic;
    mqttClient.publish(topic, String(code), { qos: 2 });
    return true;
  }

  /* Nach Broker-Umkonfiguration (z.B. auf einen entfernten Server) muss die
     WebSocket-Verbindung auf den neuen Broker umziehen. */
  async function reconnectMqtt() {
    if (mqttClient) {
      try {
        mqttClient.end(true);
      } catch (e) {
        // alte Verbindung ist egal
      }
      mqttClient = null;
    }
    await refreshConfig();
    connectMqtt();
  }

  return {
    post,
    del,
    refreshConfig,
    connectMqtt,
    reconnectMqtt,
    publishControl,
    onState(cb) { stateCallback = cb; startSse(); },
    onGameUpdate(cb) { gameUpdateCallback = cb; },
    onSound(cb) { soundCallback = cb; },
    onLobbyState(cb) { lobbyStateCallback = cb; }
  };
})();
