/* Brücke für die TV-Oberfläche: "Beenden" im Hauptmenü schließt die App.
   Im normalen Browser existiert window.dartShell nicht — die Seite zeigt
   dann stattdessen einen Hinweis. */
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('dartShell', {
  quit: () => ipcRenderer.send('quit')
});
