/**
 * Built-in Reference Mock Wi-Fi Sync Server
 * Zero-dependency standalone HTTP server for testing, CI pipelines, and protocol validation.
 */
const http = require('http');
const { createZip, readZip } = require('./lib/zip_util');

class MockSyncServer {
  constructor({ port = 8765, pin = '1234', maxPayloadBytes = 8 * 1024 * 1024 } = {}) {
    this.port = port;
    this.pairingPin = pin;
    this.maxPayloadBytes = maxPayloadBytes;
    this.server = null;

    // In-memory data store
    this.categories = [
      { id: 'cat-default-1', name: 'Genel', color: '#3b82f6', icon: 'folder', createdAt: Date.now(), updatedAt: Date.now() },
      { id: 'cat-default-2', name: 'İş & Projeler', color: '#10b981', icon: 'briefcase', createdAt: Date.now(), updatedAt: Date.now() }
    ];

    this.notes = [
      {
        id: 'note-sample-1',
        title: 'Hoş Geldiniz - Not Defterim',
        content: '# Not Defterim Wi-Fi Senkronizasyon\nMasaüstü ve mobil cihazlarınız arasında yerel ağ üzerinden hızlı ve güvenli senkronizasyon.',
        categoryId: 'cat-default-1',
        tags: ['baslangic', 'rehber'],
        color: '#1e293b',
        createdAt: Date.now(),
        updatedAt: Date.now()
      }
    ];
  }

  start() {
    return new Promise((resolve, reject) => {
      this.server = http.createServer(async (req, res) => {
        // CORS Headers
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', '*');

        if (req.method === 'OPTIONS') {
          res.writeHead(204);
          res.end();
          return;
        }

        const urlObj = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
        const pathname = urlObj.pathname;

        try {
          // 1. Status API
          if (pathname === '/api/status' || pathname === '/status') {
            const response = {
              status: 'ok',
              appName: 'Not Defterim Mock Sync Server (Node.js)',
              notesCount: this.notes.length,
              categoriesCount: this.categories.length,
              requiresPin: true,
              version: '2.1.0'
            };
            res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
            res.end(JSON.stringify(response));
            return;
          }

          // PIN validation helper
          const getProvidedPin = () => {
            return req.headers['x-sync-pin'] ||
                   req.headers['x-pairing-pin'] ||
                   urlObj.searchParams.get('pin');
          };

          // 2. Download ZIP Backup API
          if (pathname === '/api/sync/download' && req.method === 'GET') {
            const pin = getProvidedPin();
            if (pin !== this.pairingPin) {
              res.writeHead(401, { 'Content-Type': 'text/plain; charset=utf-8' });
              res.end('Geçersiz veya eksik Eşleşme PIN kodu!');
              return;
            }

            const backupInfo = {
              version: 2,
              appName: 'Not Defterim Mock Sync Server',
              timestamp: Date.now(),
              exportDate: new Date().toLocaleString('tr-TR'),
              notesCount: this.notes.length,
              categoriesCount: this.categories.length
            };

            const zipBuffer = createZip([
              { name: 'backup_info.json', data: JSON.stringify(backupInfo, null, 2) },
              { name: 'categories.json', data: JSON.stringify(this.categories, null, 2) },
              { name: 'notes.json', data: JSON.stringify(this.notes, null, 2) }
            ]);

            res.writeHead(200, {
              'Content-Type': 'application/zip',
              'Content-Disposition': 'attachment; filename="NoteApp_Mock_Sync.zip"',
              'Content-Length': zipBuffer.length
            });
            res.end(zipBuffer);
            return;
          }

          // 3. Upload / Restore Backup API
          if (pathname === '/api/sync/upload' && req.method === 'POST') {
            const pin = getProvidedPin();
            if (pin !== this.pairingPin) {
              res.writeHead(401, { 'Content-Type': 'text/plain; charset=utf-8' });
              res.end('Geçersiz veya eksik Eşleşme PIN kodu!');
              return;
            }

            // Check Content-Length for Payload Too Large
            const contentLength = parseInt(req.headers['content-length'] || '0', 10);
            if (contentLength > this.maxPayloadBytes) {
              res.writeHead(413, { 'Content-Type': 'application/json; charset=utf-8' });
              res.end(JSON.stringify({ success: false, error: 'Payload Too Large: Paket boyutu sınırı aşıldı.' }));
              return;
            }

            const chunks = [];
            let totalBytes = 0;

            req.on('data', (chunk) => {
              totalBytes += chunk.length;
              if (totalBytes > this.maxPayloadBytes) {
                res.writeHead(413, { 'Content-Type': 'application/json; charset=utf-8' });
                res.end(JSON.stringify({ success: false, error: 'Payload Too Large' }));
                req.destroy();
                return;
              }
              chunks.push(chunk);
            });

            req.on('end', () => {
              if (res.writableEnded) return;

              try {
                const bodyBuffer = Buffer.concat(chunks);

                // Unpack and validate ZIP
                let parsedArchive;
                try {
                  parsedArchive = readZip(bodyBuffer);
                } catch (zipErr) {
                  res.writeHead(400, { 'Content-Type': 'application/json; charset=utf-8' });
                  res.end(JSON.stringify({ success: false, error: `Bozuk ZIP arşivi: ${zipErr.message}` }));
                  return;
                }

                // Parse notes & categories
                let importedNotes = 0;
                let importedCategories = 0;

                const clearExisting = req.headers['x-clear-existing'] === 'true';

                if (parsedArchive['categories.json']) {
                  try {
                    const newCats = JSON.parse(parsedArchive['categories.json'].toString('utf-8'));
                    if (Array.isArray(newCats)) {
                      if (clearExisting) this.categories = [];
                      for (const c of newCats) {
                        const idx = this.categories.findIndex((existing) => existing.id === c.id);
                        if (idx >= 0) this.categories[idx] = c;
                        else this.categories.push(c);
                        importedCategories++;
                      }
                    }
                  } catch (_) {}
                }

                if (parsedArchive['notes.json']) {
                  try {
                    const newNotes = JSON.parse(parsedArchive['notes.json'].toString('utf-8'));
                    if (Array.isArray(newNotes)) {
                      if (clearExisting) this.notes = [];
                      for (const n of newNotes) {
                        const idx = this.notes.findIndex((existing) => existing.id === n.id);
                        if (idx >= 0) this.notes[idx] = n;
                        else this.notes.push(n);
                        importedNotes++;
                      }
                    }
                  } catch (_) {}
                }

                res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
                res.end(JSON.stringify({
                  success: true,
                  notesCount: importedNotes,
                  categoriesCount: importedCategories
                }));
              } catch (err) {
                res.writeHead(500, { 'Content-Type': 'application/json; charset=utf-8' });
                res.end(JSON.stringify({ success: false, error: err.message }));
              }
            });
            return;
          }

          // 4. Web Info Page
          if (pathname === '/' || pathname === '/index.html') {
            const html = `
              <!DOCTYPE html>
              <html>
              <head><meta charset="utf-8"><title>Mock Sync Server</title></head>
              <body style="font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:30px;text-align:center;">
                <h2>⚡ Not Defterim Mock Wi-Fi Sync Server</h2>
                <p>PIN: <strong style="color:#38bdf8;font-size:20px;">${this.pairingPin}</strong></p>
                <p>Mevcut Not Sayısı: ${this.notes.length}</p>
                <a href="/api/sync/download?pin=${this.pairingPin}" style="color:#38bdf8;">Yedek İndir (.zip)</a>
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

      this.server.listen(this.port, '0.0.0.0', () => {
        resolve({ port: this.port, pin: this.pairingPin });
      });

      this.server.on('error', (err) => {
        reject(err);
      });
    });
  }

  stop() {
    return new Promise((resolve) => {
      if (this.server) {
        this.server.close(() => {
          this.server = null;
          resolve();
        });
      } else {
        resolve();
      }
    });
  }
}

// Standalone execution support
if (require.main === module) {
  const args = process.argv.slice(2);
  let port = 8765;
  let pin = '1234';

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--port' && args[i + 1]) port = parseInt(args[++i], 10);
    if (args[i] === '--pin' && args[i + 1]) pin = args[++i];
  }

  const server = new MockSyncServer({ port, pin });
  server.start().then(() => {
    console.log(`\n[MockSyncServer] 🚀 Aktif: http://127.0.0.1:${port}`);
    console.log(`[MockSyncServer] 🔑 Eşleşme PIN Kodu: ${pin}`);
    console.log(`[MockSyncServer] Durdurmak için Ctrl+C tuşlarına basın.\n`);
  }).catch((err) => {
    console.error('[MockSyncServer] Başlatılamadı:', err.message);
    process.exit(1);
  });
}

module.exports = MockSyncServer;
