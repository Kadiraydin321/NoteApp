const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  // Database API
  db: {
    getActiveNotes: () => ipcRenderer.invoke('db:getActiveNotes'),
    getArchivedNotes: () => ipcRenderer.invoke('db:getArchivedNotes'),
    getTrashNotes: () => ipcRenderer.invoke('db:getTrashNotes'),
    getReminderNotes: () => ipcRenderer.invoke('db:getReminderNotes'),
    getNoteById: (id) => ipcRenderer.invoke('db:getNoteById', id),
    getNoteByTitle: (title) => ipcRenderer.invoke('db:getNoteByTitle', title),
    getNotesLinkingTo: (title, excludeId) => ipcRenderer.invoke('db:getNotesLinkingTo', title, excludeId),
    insertNote: (note) => ipcRenderer.invoke('db:insertNote', note),
    updateNote: (note) => ipcRenderer.invoke('db:updateNote', note),
    moveToTrash: (id) => ipcRenderer.invoke('db:moveToTrash', id),
    moveNotesToTrash: (ids) => ipcRenderer.invoke('db:moveNotesToTrash', ids),
    restoreFromTrash: (id) => ipcRenderer.invoke('db:restoreFromTrash', id),
    deletePermanently: (id) => ipcRenderer.invoke('db:deletePermanently', id),
    deleteNotesPermanently: (ids) => ipcRenderer.invoke('db:deleteNotesPermanently', ids),
    emptyTrash: () => ipcRenderer.invoke('db:emptyTrash'),
    getAllCategories: () => ipcRenderer.invoke('db:getAllCategories'),
    insertCategory: (cat) => ipcRenderer.invoke('db:insertCategory', cat),
    deleteCategory: (id) => ipcRenderer.invoke('db:deleteCategory', id)
  },

  // Vault & Cryptography API
  crypto: {
    hasMasterPassword: () => ipcRenderer.invoke('crypto:hasMasterPassword'),
    setupMasterPassword: (pwd) => ipcRenderer.invoke('crypto:setupMasterPassword', pwd),
    unlockVault: (pwd) => ipcRenderer.invoke('crypto:unlockVault', pwd),
    lockVault: () => ipcRenderer.invoke('crypto:lockVault'),
    isVaultUnlocked: () => ipcRenderer.invoke('crypto:isVaultUnlocked'),
    encryptContent: (content) => ipcRenderer.invoke('crypto:encryptContent', content),
    decryptContent: (content) => ipcRenderer.invoke('crypto:decryptContent', content)
  },

  // Backup & Restore API
  backup: {
    exportBackup: (vaultPwd) => ipcRenderer.invoke('backup:exportBackup', vaultPwd),
    importBackup: (vaultPwd) => ipcRenderer.invoke('backup:importBackup', vaultPwd)
  },

  // Wi-Fi Sync Server API
  sync: {
    getState: () => ipcRenderer.invoke('sync:getState'),
    start: () => ipcRenderer.invoke('sync:start'),
    stop: () => ipcRenderer.invoke('sync:stop'),
    refreshPin: () => ipcRenderer.invoke('sync:refreshPin'),
    onStateChanged: (callback) => {
      ipcRenderer.on('sync:stateChanged', (event, data) => callback(data));
    }
  },

  // System & OS Features
  system: {
    saveDrawing: (dataUrl) => ipcRenderer.invoke('system:saveDrawing', dataUrl),
    pickAttachment: () => ipcRenderer.invoke('system:pickAttachment'),
    openAttachment: (filePath) => ipcRenderer.invoke('system:openAttachment', filePath),
    exportMarkdown: (title, content) => ipcRenderer.invoke('system:exportMarkdown', title, content),
    pickBackgroundImage: () => ipcRenderer.invoke('system:pickBackgroundImage'),
    setScreenshotProtection: (enabled) => ipcRenderer.invoke('system:setScreenshotProtection', enabled),
    showNotification: (title, body) => ipcRenderer.invoke('system:showNotification', title, body)
  }
});
