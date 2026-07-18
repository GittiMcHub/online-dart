/* Fokusverwaltung: jede Ansicht ist eine vertikale Liste fokussierbarer
   Elemente (Klasse .tv-item). Hoch/Runter bewegt den Fokus, Links/Rechts und
   Bestätigen werden an die Handler der Ansicht delegiert (Stepper/Toggles).

   Bei Re-Renders (SSE) bleibt der Fokus über data-focus-id erhalten. */
const Focus = (() => {

  let items = [];
  let index = 0;
  let handlers = {};

  function apply() {
    items.forEach((el, i) => el.classList.toggle('tv-focus', i === index));
    const current = items[index];
    if (current) {
      current.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  }

  return {
    /* handlers: {onConfirm(el), onLeft(el), onRight(el), onAction(action, el)} */
    set(newItems, newHandlers, preferredId) {
      const keepId = preferredId
        || (items[index] && items[index].dataset.focusId);
      items = newItems.filter(el => el && !el.disabled);
      handlers = newHandlers || {};
      index = 0;
      if (keepId) {
        const found = items.findIndex(el => el.dataset.focusId === keepId);
        if (found >= 0) {
          index = found;
        }
      }
      apply();
    },

    clear() {
      items.forEach(el => el.classList.remove('tv-focus'));
      items = [];
      handlers = {};
    },

    current() {
      return items[index] || null;
    },

    /* Maus-/Touch-Klick (auch von Steam als Maus emulierte Controller):
       Element fokussieren und wie Bestätigen behandeln */
    activate(el) {
      const found = items.indexOf(el);
      if (found < 0) {
        return;
      }
      index = found;
      apply();
      if (handlers.onConfirm) {
        handlers.onConfirm(el);
      }
    },

    handle(action) {
      const el = items[index];
      switch (action) {
        case 'up':
          if (items.length > 0) {
            index = (index - 1 + items.length) % items.length;
            apply();
          }
          return true;
        case 'down':
          if (items.length > 0) {
            index = (index + 1) % items.length;
            apply();
          }
          return true;
        case 'left':
          if (el && handlers.onLeft) {
            handlers.onLeft(el);
          }
          return true;
        case 'right':
          if (el && handlers.onRight) {
            handlers.onRight(el);
          }
          return true;
        case 'confirm':
          if (el && handlers.onConfirm) {
            handlers.onConfirm(el);
          }
          return true;
        default:
          if (handlers.onAction) {
            handlers.onAction(action, el);
            return true;
          }
          return false;
      }
    }
  };
})();
