const http = require('http');
const os = require('os');
const fs = require('fs');
const path = require('path');

class SyncServer {
  constructor(dbManager, backupManager, port = 8765, onStateChanged = () => {}) {
    this.dbManager = dbManager;
    this.backupManager = backupManager;
    this.port = port;
    this.onStateChanged = onStateChanged;
    this.server = null;
    this.pairingPin = this.generatePin();
    this.lastMessage = 'Durduruldu';
  }

  generatePin() {
    return Math.floor(1000 + Math.random() * 9000).toString();
  }

  getLocalIpAddress() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
      for (const iface of interfaces[name]) {
        if (iface.family === 'IPv4' && !iface.internal) {
          if (!iface.address.startsWith('127.')) {
            return iface.address;
          }
        }
      }
    }
    return '127.0.0.1';
  }

  isRunning() {
    return Boolean(this.server && this.server.listening);
  }

  refreshPin() {
    this.pairingPin = this.generatePin();
    this.notifyState();
  }

  notifyState() {
    this.onStateChanged({
      isRunning: this.isRunning(),
      localIp: this.getLocalIpAddress(),
      port: this.port,
      pairingPin: this.pairingPin,
      lastSyncMessage: this.lastMessage
    });
  }

  start() {
    if (this.server) return;
    this.pairingPin = this.generatePin();
    const localIp = this.getLocalIpAddress();

    this.server = http.createServer(async (req, res) => {
      // CORS headers
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
      res.setHeader('Access-Control-Allow-Headers', '*');

      if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
      }

      const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
      const pathname = urlObj.pathname;

      try {
        // 1. Status API
        if (pathname === '/api/status' && req.method === 'GET') {
          const notes = this.dbManager.getAllNotes();
          const response = {
            status: 'ok',
            appName: 'Not Defterim Masaüstü (Electron)',
            notesCount: notes.length,
            requiresPin: true
          };
          res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
          res.end(JSON.stringify(response));
          return;
        }

        // 2. Upload / Import Backup API
        if (pathname === '/api/sync/upload' && req.method === 'POST') {
          const pin = req.headers['x-sync-pin'];
          if (pin !== this.pairingPin) {
            res.writeHead(401, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Geçersiz Eşleşme PIN kodu!');
            return;
          }

          const chunks = [];
          req.on('data', chunk => chunks.push(chunk));
          req.on('end', async () => {
            try {
              const bodyBuffer = Buffer.concat(chunks);
              const vaultPassword = req.headers['x-vault-password'] || null;
              const clearExisting = req.headers['x-clear-existing'] === 'true';

              const result = await this.backupManager.importBackup(bodyBuffer, clearExisting, vaultPassword);
              if (result.success) {
                this.lastMessage = `Başarılı senkronizasyon: ${result.notesCount} not alındı.`;
                res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
                res.end(JSON.stringify({
                  success: true,
                  notesCount: result.notesCount,
                  categoriesCount: result.categoriesCount || 0,
                  attachmentsCount: result.attachmentsCount || 0
                }));
              } else if (result.needsPassword) {
                res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
                res.end(JSON.stringify({ success: false, error: 'Kasa şifresi gerekiyor.', needsPassword: true }));
              } else {
                this.lastMessage = `Senkronizasyon hatası: ${result.message}`;
                res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
                res.end(JSON.stringify({ success: false, error: result.message }));
              }
              this.notifyState();
            } catch (err) {
              res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ success: false, error: err.message }));
            }
          });
          return;
        }

        // 3. Download / Export Backup API
        if (pathname === '/api/sync/download' && req.method === 'GET') {
          const pin = req.headers['x-sync-pin'] || urlObj.searchParams.get('pin');
          if (pin !== this.pairingPin) {
            res.writeHead(401, { 'Content-Type': 'text/plain; charset=utf-8' });
            res.end('Geçersiz Eşleşme PIN kodu!');
            return;
          }

          const { zipBuffer, notesCount, attachmentsCount } = await this.backupManager.buildZipArchiveBuffer();
          res.writeHead(200, {
            'Content-Type': 'application/zip',
            'Content-Disposition': 'attachment; filename="NoteApp_Sync.zip"',
            'Content-Length': zipBuffer.length
          });
          res.end(zipBuffer);
          this.lastMessage = `${notesCount} not ve ${attachmentsCount} dosya telefona aktarıldı.`;
          this.notifyState();
          return;
        }

        // 4. Web Interface (Telefondan web tarayıcısıyla açılabilir)
        if (pathname === '/' || pathname === '/index.html') {
          const notes = this.dbManager.getAllNotes();
          const html = `
            <!DOCTYPE html>
            <html lang="tr">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Not Defterim - Wi-Fi Senkronizasyon</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0b1120; color: #f8fafc; margin: 0; padding: 20px; display: flex; justify-content: center; align-items: center; min-height: 100vh; }
                .card { background: #1e293b; border: 1px solid #334155; border-radius: 16px; padding: 32px; max-width: 480px; width: 100%; box-shadow: 0 10px 25px rgba(0,0,0,0.5); text-align: center; }
                h1 { font-size: 20px; margin-bottom: 8px; color: #38bdf8; }
                p { color: #94a3b8; font-size: 14px; margin-bottom: 24px; }
                .badge { display: inline-block; background: #0f172a; border: 1px solid #38bdf8; border-radius: 8px; padding: 8px 16px; font-size: 24px; font-weight: bold; letter-spacing: 4px; color: #38bdf8; margin-bottom: 24px; }
                .btn { display: block; width: 100%; padding: 12px; margin: 8px 0; border: none; border-radius: 10px; font-size: 15px; font-weight: 600; cursor: pointer; text-decoration: none; box-sizing: border-box; }
                .btn-primary { background: #2563eb; color: #fff; }
                .stat { font-size: 13px; color: #64748b; margin-top: 16px; }
              </style>
            </head>
            <body>
              <div class="card">
                <h1>Not Defterim Wi-Fi Senkronizasyon</h1>
                <p>Android cihazınızdaki uygulama ile veya doğrudan tarayıcı üzerinden notlarınızı senkronize edin.</p>
                <div>Eşleşme PIN Kodu:</div>
                <div class="badge">${this.pairingPin}</div>
                <a class="btn btn-primary" href="/api/sync/download?pin=${this.pairingPin}">Masaüstünden Yedek İndir (.zip)</a>
                <div class="stat">Masaüstündeki Not Sayısı: ${notes.length}</div>
              </div>
            </body>
            </html>
          `;
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          res.end(html);
          return;
        }

        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Not Found');
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('Server Error: ' + err.message);
      }
    });

    return new Promise((resolve, reject) => {
      this.server.listen(this.port, '0.0.0.0', () => {
        this.lastMessage = `Wi-Fi Sunucusu aktif: http://${localIp}:${this.port}`;
        this.notifyState();
        resolve();
      });

      this.server.on('error', err => {
        this.lastMessage = `Sunucu hatası: ${err.message}`;
        this.server = null;
        this.notifyState();
        reject(err);
      });
    });
  }

  stop() {
    return new Promise((resolve) => {
      if (this.server) {
        this.server.close(() => {
          this.server = null;
          this.lastMessage = 'Sunucu durduruldu';
          this.notifyState();
          resolve();
        });
      } else {
        resolve();
      }
    });
  }
}

module.exports = SyncServer;
