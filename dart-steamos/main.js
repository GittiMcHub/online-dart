/* Electron-Hülle für SteamOS: startet den gebündelten onion-server (JRE + Jar
   aus resources/app-runtime), wartet bis das Web-Interface antwortet und zeigt
   dann die TV-Oberfläche (/tv/) im Vollbild. Beim Beenden bekommt der Server
   SIGTERM — sein Shutdown-Hook stoppt Broker, BLE und Webserver sauber. */
const { app, BrowserWindow, dialog, ipcMain } = require('electron');
const { spawn } = require('child_process');
const fs = require('fs');
const http = require('http');
const net = require('net');
const path = require('path');

let child = null;
let win = null;
let quitting = false;

// Sounds der Spielanzeige sollen ohne Klick/Tastendruck abspielen
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      win.focus();
    }
  });
}

/** Gebündelte Laufzeit (AppImage) oder Entwicklungs-Fallback (System-Java +
    lokal gebautes Fat-Jar aus dem Repo). */
function resolveRuntime() {
  const runtime = path.join(process.resourcesPath, 'app-runtime');
  if (fs.existsSync(runtime)) {
    return {
      java: path.join(runtime, 'jre', 'bin', 'java'),
      jar: path.join(runtime, 'onion-server-all.jar')
    };
  }
  const libs = path.join(__dirname, '..', 'dart-server', 'onion-server', 'build', 'libs');
  const jar = fs.existsSync(libs)
    ? fs.readdirSync(libs).find((name) => /^onion-server-.*-all\.jar$/.test(name))
    : null;
  if (!jar) {
    return null;
  }
  return { java: 'java', jar: path.join(libs, jar) };
}

function findFreePort(start) {
  return new Promise((resolve, reject) => {
    const probe = (port) => {
      if (port > start + 20) {
        reject(new Error('Kein freier Port ab ' + start));
        return;
      }
      const server = net.createServer();
      server.once('error', () => probe(port + 1));
      server.once('listening', () => server.close(() => resolve(port)));
      server.listen(port, '127.0.0.1');
    };
    probe(start);
  });
}

function pollHttp(url, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  return new Promise((resolve, reject) => {
    const attempt = () => {
      const request = http.get(url, (response) => {
        response.resume();
        resolve();
      });
      request.on('error', () => {
        if (Date.now() > deadline) {
          reject(new Error('Server antwortet nicht auf ' + url));
        } else {
          setTimeout(attempt, 500);
        }
      });
    };
    attempt();
  });
}

function fail(message) {
  dialog.showErrorBox('Online-Dart', message);
  app.quit();
}

app.whenReady().then(async () => {
  const runtime = resolveRuntime();
  if (!runtime) {
    fail('Server-Jar nicht gefunden. Bitte zuerst bauen: '
      + 'cd dart-server/onion-server && ./gradlew fatJar');
    return;
  }

  let webPort;
  let wsPort;
  try {
    webPort = await findFreePort(8420);
    wsPort = await findFreePort(8083);
  } catch (error) {
    fail(error.message);
    return;
  }

  child = spawn(runtime.java, [
    '-jar', runtime.jar,
    '--mode', 'combined',
    '--embedded-broker',
    '--auto-broker',
    '--web-port', String(webPort),
    '--embedded-broker-ws-port', String(wsPort),
    '--server-name', 'Steam-Dart'
  ], { stdio: 'inherit' });

  child.on('exit', (code) => {
    child = null;
    if (!quitting) {
      fail('Der Dart-Server wurde unerwartet beendet (Code ' + code + '). '
        + 'Läuft eventuell schon eine andere Instanz oder ist Port 1883 belegt?');
    }
  });

  try {
    await pollHttp('http://127.0.0.1:' + webPort + '/api/state', 30000);
  } catch (error) {
    fail(error.message);
    return;
  }

  win = new BrowserWindow({
    fullscreen: true,
    autoHideMenuBar: true,
    backgroundColor: '#10131a',
    webPreferences: { preload: path.join(__dirname, 'preload.js') }
  });
  win.loadURL('http://127.0.0.1:' + webPort + '/tv/');
  win.on('closed', () => {
    win = null;
    app.quit();
  });
});

ipcMain.on('quit', () => app.quit());

app.on('before-quit', () => {
  quitting = true;
  if (child) {
    child.kill('SIGTERM');
    setTimeout(() => {
      if (child) {
        child.kill('SIGKILL');
      }
    }, 5000).unref();
  }
});

app.on('window-all-closed', () => app.quit());
