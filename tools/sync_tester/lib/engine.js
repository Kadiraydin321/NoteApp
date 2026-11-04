/**
 * Cross-Platform LAN / Wi-Fi Sync Test Engine
 * Sıfır harici bağımlılık - Tamamen yerleşik Node.js modülleri ile çalışır.
 */

const http = require('http');
const https = require('https');
const { URL } = require('url');

class SyncTestEngine {
  constructor(options = {}) {
    this.host = options.host || '127.0.0.1';
    this.port = options.port || 8765;
    this.pin = options.pin || null;
    this.timeout = options.timeout || 6000;
    this.results = [];
    this.startTime = null;
    this.endTime = null;
  }

  get baseUrl() {
    return `http://${this.host}:${this.port}`;
  }

  async runHttp(method, path, options = {}) {
    const urlStr = `${this.baseUrl}${path.startsWith('/') ? path : '/' + path}`;
    const parsed = new URL(urlStr);
    const postData = options.body ? (Buffer.isBuffer(options.body) ? options.body : Buffer.from(options.body)) : null;

    const headers = { ...options.headers };
    if (postData && !headers['Content-Length'] && !headers['content-length']) {
      headers['Content-Length'] = postData.length;
    }

    const t0 = Date.now();
    return new Promise((resolve) => {
      const req = http.request({
        hostname: parsed.hostname,
        port: parsed.port,
        path: parsed.pathname + parsed.search,
        method: method.toUpperCase(),
        headers: headers,
        timeout: options.timeout || this.timeout,
      }, (res) => {
        const chunks = [];
        res.on('data', chunk => chunks.push(chunk));
        res.on('end', () => {
          const latency = Date.now() - t0;
          const bodyBuffer = Buffer.concat(chunks);
          let json = null;
          try {
            json = JSON.parse(bodyBuffer.toString('utf8'));
          } catch (_) {}

          resolve({
            ok: res.statusCode >= 200 && res.statusCode < 300,
            statusCode: res.statusCode,
            headers: res.headers,
            bodyBuffer,
            bodyText: bodyBuffer.toString('utf8'),
            json,
            latency
          });
        });
      });

      req.on('timeout', () => {
        req.destroy();
        resolve({
          ok: false,
          statusCode: 0,
          error: 'TIMEOUT',
          latency: Date.now() - t0
        });
      });

      req.on('error', (err) => {
        resolve({
          ok: false,
          statusCode: 0,
          error: err.code || err.message,
          latency: Date.now() - t0
        });
      });

      if (postData) {
        req.write(postData);
      }
      req.end();
    });
  }

  async assertTest(category, name, testFn) {
    const t0 = Date.now();
    try {
      const outcome = await testFn();
      const duration = Date.now() - t0;
      const result = {
        category,
        name,
        passed: outcome.passed,
        message: outcome.message || (outcome.passed ? 'Başarılı' : 'Beklenen koşul sağlanamadı'),
        duration,
        details: outcome.details || null
      };
      this.results.push(result);
      return result;
    } catch (err) {
      const duration = Date.now() - t0;
      const result = {
        category,
        name,
        passed: false,
        message: `Hata: ${err.message}`,
        duration,
        error: err.stack
      };
      this.results.push(result);
      return result;
    }
  }

  getSummary() {
    const total = this.results.length;
    const passed = this.results.filter(r => r.passed).length;
    const failed = total - passed;
    const totalDuration = this.results.reduce((acc, r) => acc + r.duration, 0);

    return {
      total,
      passed,
      failed,
      passRate: total > 0 ? ((passed / total) * 100).toFixed(1) : 0,
      totalDuration,
      results: this.results
    };
  }
}

module.exports = { SyncTestEngine };
