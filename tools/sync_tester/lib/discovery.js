const net = require('net');

class DiscoveryProbe {
  static async checkPortOpen(host, port, timeout = 2000) {
    return new Promise((resolve) => {
      const socket = new net.Socket();
      socket.setTimeout(timeout);

      socket.on('connect', () => {
        socket.destroy();
        resolve(true);
      });

      socket.on('timeout', () => {
        socket.destroy();
        resolve(false);
      });

      socket.on('error', () => {
        socket.destroy();
        resolve(false);
      });

      socket.connect(port, host);
    });
  }

  static async probeServer(engine) {
    const res = await engine.runHttp('GET', '/api/status');
    if (res.ok && res.json) {
      return {
        online: true,
        endpoint: '/api/status',
        info: res.json,
        latency: res.latency
      };
    }

    // Fallback: /status
    const fallback = await engine.runHttp('GET', '/status');
    if (fallback.ok && fallback.json) {
      return {
        online: true,
        endpoint: '/status',
        info: fallback.json,
        latency: fallback.latency
      };
    }

    return {
      online: false,
      statusCode: res.statusCode || fallback.statusCode,
      error: res.error || fallback.error
    };
  }
}

module.exports = { DiscoveryProbe };
