const { app, BrowserWindow, Menu, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');

let mainWindow;

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 720,
    minWidth: 380,
    minHeight: 560,
    title: '智能记账',
    backgroundColor: '#f8fafc',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      preload: path.join(__dirname, 'preload.js'),
    },
  });

  // 隐藏菜单栏
  Menu.setApplicationMenu(null);

  mainWindow.loadFile('index.html');

  // 外部链接用浏览器打开
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
}

// 数据持久化 IPC - 使用用户数据目录存储
ipcMain.handle('data:save', async (_event, key, value) => {
  const dataPath = path.join(app.getPath('userData'), 'data.json');
  let data = {};
  try {
    if (fs.existsSync(dataPath)) {
      data = JSON.parse(fs.readFileSync(dataPath, 'utf-8'));
    }
  } catch (e) {
    console.error('Read data error:', e);
  }
  data[key] = value;
  fs.writeFileSync(dataPath, JSON.stringify(data), 'utf-8');
  return true;
});

ipcMain.handle('data:load', async (_event, key) => {
  const dataPath = path.join(app.getPath('userData'), 'data.json');
  try {
    if (fs.existsSync(dataPath)) {
      const data = JSON.parse(fs.readFileSync(dataPath, 'utf-8'));
      return data[key] || null;
    }
  } catch (e) {
    console.error('Load data error:', e);
  }
  return null;
});

ipcMain.handle('data:remove', async (_event, key) => {
  const dataPath = path.join(app.getPath('userData'), 'data.json');
  try {
    if (fs.existsSync(dataPath)) {
      const data = JSON.parse(fs.readFileSync(dataPath, 'utf-8'));
      delete data[key];
      fs.writeFileSync(dataPath, JSON.stringify(data), 'utf-8');
    }
  } catch (e) {
    console.error('Remove data error:', e);
  }
  return true;
});

ipcMain.handle('data:exportFile', async (_event, content, defaultName) => {
  const { dialog } = require('electron');
  const result = await dialog.showSaveDialog(mainWindow, {
    title: '导出数据',
    defaultPath: defaultName,
    filters: [{ name: 'JSON 文件', extensions: ['json'] }],
  });
  if (!result.canceled && result.filePath) {
    fs.writeFileSync(result.filePath, content, 'utf-8');
    return true;
  }
  return false;
});

ipcMain.handle('data:importFile', async () => {
  const { dialog } = require('electron');
  const result = await dialog.showOpenDialog(mainWindow, {
    title: '导入数据',
    properties: ['openFile'],
    filters: [{ name: 'JSON 文件', extensions: ['json'] }],
  });
  if (!result.canceled && result.filePaths.length > 0) {
    return fs.readFileSync(result.filePaths[0], 'utf-8');
  }
  return null;
});

app.whenReady().then(() => {
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
