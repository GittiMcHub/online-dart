/* Eingabeschicht: Gamepad (Gamepad API) + Tastatur (TV-Fernbedienungen senden
   Pfeiltasten/Enter als HID-Tasten) werden auf einheitliche Aktionen abgebildet
   und als CustomEvent 'tv-action' auf document verteilt.

   Aktionen: up, down, left, right, confirm, back, action-x, action-y, menu */
const TvInput = (() => {

  const REPEAT_DELAY_MS = 400;
  const REPEAT_INTERVAL_MS = 120;
  const STICK_DEADZONE = 0.5;
  const DIRECTIONS = new Set(['up', 'down', 'left', 'right']);

  // Standard-Gamepad-Mapping (Xbox-Layout, deckt Steam Input "Gamepad" ab)
  const BUTTON_ACTIONS = {
    0: 'confirm',   // A
    1: 'back',      // B
    2: 'action-x',  // X
    3: 'action-y',  // Y
    4: 'lb',        // Bumper links
    5: 'rb',        // Bumper rechts
    9: 'menu',      // Start
    12: 'up',       // D-Pad
    13: 'down',
    14: 'left',
    15: 'right'
  };

  const KEY_ACTIONS = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
    Enter: 'confirm',
    ' ': 'confirm',
    Escape: 'back',
    Backspace: 'back',
    x: 'action-x',
    y: 'action-y',
    q: 'lb',
    e: 'rb',
    PageUp: 'lb',
    PageDown: 'rb',
    f: 'menu'
  };

  let enabled = true;
  // pro Aktion: {since, lastRepeat} — für Auto-Repeat der Richtungen
  const held = new Map();
  let rafRunning = false;

  function emit(action, repeat) {
    if (!enabled) {
      return;
    }
    document.dispatchEvent(new CustomEvent('tv-action', { detail: { action, repeat: !!repeat } }));
  }

  function pollGamepads(now) {
    const pads = navigator.getGamepads ? navigator.getGamepads() : [];
    const active = new Set();
    for (const pad of pads) {
      if (!pad) {
        continue;
      }
      for (const [index, action] of Object.entries(BUTTON_ACTIONS)) {
        if (pad.buttons[index] && pad.buttons[index].pressed) {
          active.add(action);
        }
      }
      // linker Stick zusätzlich zu D-Pad
      if (pad.axes.length >= 2) {
        if (pad.axes[0] < -STICK_DEADZONE) active.add('left');
        if (pad.axes[0] > STICK_DEADZONE) active.add('right');
        if (pad.axes[1] < -STICK_DEADZONE) active.add('up');
        if (pad.axes[1] > STICK_DEADZONE) active.add('down');
      }
    }

    for (const action of active) {
      const state = held.get(action);
      if (!state) {
        held.set(action, { since: now, lastRepeat: now });
        emit(action, false);
      } else if (DIRECTIONS.has(action)
          && now - state.since > REPEAT_DELAY_MS
          && now - state.lastRepeat > REPEAT_INTERVAL_MS) {
        state.lastRepeat = now;
        emit(action, true);
      }
    }
    for (const action of [...held.keys()]) {
      if (!active.has(action)) {
        held.delete(action);
      }
    }
  }

  function loop(now) {
    pollGamepads(now);
    requestAnimationFrame(loop);
  }

  function onKeyDown(event) {
    // Bei offener Bildschirmtastatur tippt man direkt — osk.js behandelt das
    if (typeof Osk !== 'undefined' && Osk.isOpen() && Osk.handleKey(event)) {
      event.preventDefault();
      return;
    }
    const action = KEY_ACTIONS[event.key] || KEY_ACTIONS[event.key.toLowerCase()];
    if (!action) {
      return;
    }
    if (!DIRECTIONS.has(action) && event.repeat) {
      return;
    }
    event.preventDefault();
    emit(action, event.repeat);
  }

  return {
    start() {
      if (!rafRunning) {
        rafRunning = true;
        requestAnimationFrame(loop);
        document.addEventListener('keydown', onKeyDown);
      }
    },
    setEnabled(value) {
      enabled = value;
    }
  };
})();
