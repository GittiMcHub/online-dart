/* Bildschirmtastatur für Spielernamen: 2D-Raster mit D-Pad-Navigation.
   Ⓐ/Klick aktiviert die fokussierte Taste, Ⓑ Abbrechen, Ⓧ Löschen, Ⓨ Fertig.
   Die letzte Rasterzeile enthält Aktionstasten (␣ ⌫ ✗ ✓), damit auch
   Controller funktionieren, die Steam als Maus/Tastatur emuliert.
   Mit echter Tastatur kann direkt getippt werden; Enter beendet dann die
   Eingabe — nach Rasternavigation aktiviert Enter stattdessen die Taste. */
const Osk = (() => {

  const CHAR_KEYS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜß0123456789.-'.split('')
    .map((char) => ({ label: char, char }));
  const ACTION_KEYS = [
    { label: '␣', action: 'space' },
    { label: '⌫', action: 'delete' },
    { label: '✗', action: 'cancel' },
    { label: '✓', action: 'done' }
  ];
  const KEYS = [...CHAR_KEYS, ...ACTION_KEYS];
  const COLUMNS = 10;
  const DEFAULT_MAX_LENGTH = 12;

  let open = false;
  let value = '';
  let cursor = 0;
  let typedDirectly = false;   // zuletzt getippt statt navigiert → Enter = Fertig
  let autoCase = true;         // Namen: erster Buchstabe groß; Adressen/Passwörter: aus
  let maxLength = DEFAULT_MAX_LENGTH;
  let doneCallback = null;
  let cancelCallback = null;

  const el = (id) => document.getElementById(id);

  function render() {
    el('oskValue').textContent = value || ' ';
    const grid = el('oskGrid');
    if (grid.childElementCount === 0) {
      KEYS.forEach((key, i) => {
        const button = document.createElement('div');
        button.className = 'osk-key';
        button.textContent = key.label;
        button.addEventListener('click', () => {
          cursor = i;
          typedDirectly = false;
          activate(i);
        });
        grid.appendChild(button);
      });
    }
    [...grid.children].forEach((child, i) =>
      child.classList.toggle('tv-focus', i === cursor));
  }

  function close() {
    open = false;
    el('osk').hidden = true;
  }

  function finish() {
    close();
    if (doneCallback) {
      doneCallback(value.trim());
    }
  }

  function cancel() {
    close();
    if (cancelCallback) {
      cancelCallback();
    }
  }

  function append(char) {
    if (value.length >= maxLength) {
      return;
    }
    if (!autoCase) {
      value += char.toLowerCase();
      return;
    }
    // erster Buchstabe groß, Rest klein — übliche Namensschreibweise
    // (ß nie über toUpperCase: das ergäbe "SS")
    value += value.length === 0 && char !== 'ß' ? char.toUpperCase() : char.toLowerCase();
  }

  function activate(index) {
    const key = KEYS[index];
    if (key.char) {
      append(key.char);
    } else if (key.action === 'space') {
      if (value.length > 0 && value.length < maxLength) {
        value += ' ';
      }
    } else if (key.action === 'delete') {
      value = value.slice(0, -1);
    } else if (key.action === 'done') {
      finish();
      return;
    } else if (key.action === 'cancel') {
      cancel();
      return;
    }
    render();
  }

  return {
    isOpen() {
      return open;
    },

    /* options: {autoCase: false} für Adressen/Passwörter, {initial: '...'},
       {maxLength: 40} für Hostnamen */
    open(title, onDone, onCancel, options) {
      open = true;
      value = (options && options.initial) || '';
      cursor = 0;
      typedDirectly = false;
      autoCase = !options || options.autoCase !== false;
      maxLength = (options && options.maxLength) || DEFAULT_MAX_LENGTH;
      doneCallback = onDone;
      cancelCallback = onCancel || null;
      el('oskTitle').textContent = title;
      el('osk').hidden = false;
      render();
    },

    /* Controller-Aktionen, von app.js durchgereicht, solange offen */
    handleAction(action) {
      const rows = Math.ceil(KEYS.length / COLUMNS);
      const row = Math.floor(cursor / COLUMNS);
      const col = cursor % COLUMNS;
      switch (action) {
        case 'left':
          cursor = row * COLUMNS + (col - 1 + COLUMNS) % COLUMNS;
          typedDirectly = false;
          break;
        case 'right':
          cursor = row * COLUMNS + (col + 1) % COLUMNS;
          typedDirectly = false;
          break;
        case 'up':
          cursor = ((row - 1 + rows) % rows) * COLUMNS + col;
          typedDirectly = false;
          break;
        case 'down':
          cursor = ((row + 1) % rows) * COLUMNS + col;
          typedDirectly = false;
          break;
        case 'confirm':
          typedDirectly = false;
          activate(Math.min(cursor, KEYS.length - 1));
          return;
        case 'action-x':
          value = value.slice(0, -1);
          break;
        case 'action-y':
        case 'menu':
          finish();
          return;
        case 'back':
          cancel();
          return;
      }
      cursor = Math.min(cursor, KEYS.length - 1);
      render();
    },

    /* Direkte Tastatureingabe; true = Ereignis wurde verbraucht */
    handleKey(event) {
      if (event.key === 'Enter') {
        // Nach direktem Tippen beendet Enter die Eingabe; nach Navigation
        // (auch durch Steam-Tastatur-Emulation) aktiviert es die Taste
        if (typedDirectly) {
          finish();
        } else {
          activate(Math.min(cursor, KEYS.length - 1));
        }
        return true;
      }
      if (event.key === 'Escape') {
        cancel();
        return true;
      }
      if (event.key === 'Backspace') {
        value = value.slice(0, -1);
        render();
        return true;
      }
      if (event.key.length === 1 && /[\p{L}\p{N} .\-]/u.test(event.key)) {
        if (value.length < maxLength) {
          value += event.key;
        }
        typedDirectly = true;
        render();
        return true;
      }
      // Pfeiltasten etc. weiter an die normale Aktionsbehandlung
      return false;
    }
  };
})();
