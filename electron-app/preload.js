const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopAPI', {
  saveData: (key, value) => ipcRenderer.invoke('data:save', key, value),
  loadData: (key) => ipcRenderer.invoke('data:load', key),
  removeData: (key) => ipcRenderer.invoke('data:remove', key),
  exportFile: (content, defaultName) => ipcRenderer.invoke('data:exportFile', content, defaultName),
  importFile: () => ipcRenderer.invoke('data:importFile'),
});
