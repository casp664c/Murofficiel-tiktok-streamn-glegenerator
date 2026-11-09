import http from 'node:http';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const SERVICE = 'live-key-studio-backend';

function json(res, status, data) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer'
  });
  res.end(JSON.stringify(data));
}

/** Pure HTTP server; does not store or accept TikTok account credentials. */
export function createAppServer() {
  return http.createServer((req, res) => {
    let path;
    try {
      path = new URL(req.url || '/', 'http://localhost').pathname;
    } catch {
      return json(res, 400, { error: 'BAD_URL' });
    }

    if (req.method === 'GET' && path === '/health') {
      return json(res, 200, { service: SERVICE, status: 'ok', version: '0.1.0-dev' });
    }

    if (req.method === 'GET' && path === '/api/v1/capabilities') {
      return json(res, 200, {
        service: SERVICE,
        tiktokLiveKeyIssuance: false,
        tiktokLogin: false,
        manualRtmpExport: true,
        note: 'No TikTok integration has been verified or enabled.'
      });
    }

    if (req.method === 'POST' && path === '/api/v1/sessions') {
      // This endpoint is deliberately unavailable until a lawful, authorized
      // provider is implemented and tested. Never invent streaming keys.
      req.resume();
      return json(res, 501, {
        error: 'TIKTOK_PROVIDER_NOT_CONFIGURED',
        message: 'No verified TikTok LIVE session provider is installed.'
      });
    }

    if (req.method === 'OPTIONS') {
      return json(res, 405, { error: 'CORS_NOT_ENABLED' });
    }

    return json(res, 404, { error: 'NOT_FOUND' });
  });
}

// Altare / container processes set HOST and PORT explicitly.
// Default binds only to loopback to avoid accidental exposure.
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const host = process.env.HOST || '127.0.0.1';
  const port = Number(process.env.PORT || '3000');
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    process.stderr.write('PORT must be an integer from 1 to 65535\n');
    process.exitCode = 1;
  } else {
    const server = createAppServer();
    server.listen(port, host, () => {
      process.stdout.write('Live Key Studio backend listening on ' + host + ':' + port + '\n');
    });
  }
}
