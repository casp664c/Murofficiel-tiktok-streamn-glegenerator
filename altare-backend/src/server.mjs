import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import {
  burnPasswordCheck,
  hashPassword,
  newToken,
  safeEqual,
  tokenId,
  verifyPassword
} from './auth.mjs';
import { RateLimiter } from './ratelimit.mjs';
import { Store } from './store.mjs';
import {
  normalizeUsername,
  validPassword,
  validProfileName,
  validRtmpServer,
  validStreamKey
} from './validate.mjs';
import { open, parseVaultKey, seal } from './vault.mjs';

const SERVICE = 'live-key-studio-backend';
const VERSION = '0.2.0-dev';
const MAX_BODY = 16 * 1024;
const MAX_PROFILES = 50;
const WINDOW = 15 * 60 * 1000;

function json(res, status, data) {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer'
  });
  res.end(JSON.stringify(data));
}

class HttpError extends Error {
  constructor(status, code) {
    super(code);
    this.status = status;
    this.code = code;
  }
}

async function readJson(req) {
  const type = (req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
  if (type !== 'application/json') throw new HttpError(415, 'JSON_REQUIRED');
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > MAX_BODY) throw new HttpError(413, 'BODY_TOO_LARGE');
    chunks.push(chunk);
  }
  try {
    const value = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error();
    return value;
  } catch {
    throw new HttpError(400, 'BAD_JSON');
  }
}

function publicProfile(p) {
  return {
    id: p.id,
    name: p.name,
    server: p.server,
    hasKey: Boolean(p.sealedKey),
    createdAt: p.createdAt,
    updatedAt: p.updatedAt
  };
}

/**
 * Pure HTTP server. It never contacts TikTok, never stores TikTok account
 * credentials, and never fabricates stream keys. Users may store their own
 * RTMP profiles; stream keys are encrypted at rest and only when VAULT_KEY is set.
 *
 * options:
 *  - dataDir: directory for store.json (omit for in-memory)
 *  - vaultKey: Buffer(32) enabling encrypted key storage
 *  - allowRegistration: open sign-up (default false)
 *  - registrationCode: invite code required to sign up (default none)
 *  - trustProxy: use the right-most X-Forwarded-For entry as client address
 *  - sessionDays: login lifetime (default 30)
 *  - now: clock override for tests
 */
export function createAppServer(options = {}) {
  const {
    dataDir = null,
    vaultKey = null,
    allowRegistration = false,
    registrationCode = null,
    trustProxy = false,
    sessionDays = 30,
    now = () => Date.now()
  } = options;

  const store = new Store(dataDir);
  const limiter = new RateLimiter(now);
  const registrationOpen = allowRegistration || Boolean(registrationCode);

  function clientAddress(req) {
    if (trustProxy) {
      const forwarded = String(req.headers['x-forwarded-for'] || '').split(',');
      const last = forwarded[forwarded.length - 1].trim();
      if (last) return last;
    }
    return req.socket.remoteAddress || 'unknown';
  }

  function authenticate(req) {
    const header = req.headers.authorization || '';
    const match = /^Bearer ([A-Za-z0-9_-]{20,100})$/.exec(header);
    if (!match) throw new HttpError(401, 'UNAUTHORIZED');
    const hash = tokenId(match[1]);
    const session = store.getSession(hash, now());
    if (!session) throw new HttpError(401, 'UNAUTHORIZED');
    const user = store.getUser(session.userId);
    if (!user) throw new HttpError(401, 'UNAUTHORIZED');
    return { user, tokenHash: hash };
  }

  async function issueSession(user) {
    const token = newToken();
    const expiresAt = now() + sessionDays * 24 * 60 * 60 * 1000;
    store.addSession(tokenId(token), { userId: user.id, createdAt: now(), expiresAt });
    await store.save();
    return { token, expiresAt };
  }

  // ---- auth handlers
  async function register(req, res) {
    if (!registrationOpen) throw new HttpError(403, 'REGISTRATION_CLOSED');
    if (!limiter.hit('reg:' + clientAddress(req), 10, WINDOW)) throw new HttpError(429, 'RATE_LIMITED');
    const body = await readJson(req);
    if (registrationCode && !safeEqual(body.inviteCode ?? '', registrationCode)) {
      throw new HttpError(403, 'INVALID_INVITE_CODE');
    }
    const username = normalizeUsername(body.username);
    if (!username) throw new HttpError(400, 'INVALID_USERNAME');
    if (!validPassword(body.password)) throw new HttpError(400, 'INVALID_PASSWORD');
    if (store.findUserByName(username)) throw new HttpError(409, 'USERNAME_TAKEN');
    const user = {
      id: randomUUID(),
      username,
      passwordHash: await hashPassword(body.password),
      createdAt: now()
    };
    store.addUser(user);
    const session = await issueSession(user);
    json(res, 201, { user: { id: user.id, username }, ...session });
  }

  async function login(req, res) {
    const address = clientAddress(req);
    if (!limiter.hit('login-ip:' + address, 30, WINDOW)) throw new HttpError(429, 'RATE_LIMITED');
    const body = await readJson(req);
    const username = normalizeUsername(body.username);
    if (!username || typeof body.password !== 'string' || body.password.length > 128) {
      throw new HttpError(401, 'INVALID_CREDENTIALS');
    }
    if (!limiter.hit('login-user:' + username, 8, WINDOW)) throw new HttpError(429, 'RATE_LIMITED');
    const user = store.findUserByName(username);
    if (!user) {
      await burnPasswordCheck(body.password);
      throw new HttpError(401, 'INVALID_CREDENTIALS');
    }
    if (!(await verifyPassword(body.password, user.passwordHash))) {
      throw new HttpError(401, 'INVALID_CREDENTIALS');
    }
    limiter.reset('login-user:' + username);
    const session = await issueSession(user);
    json(res, 200, { user: { id: user.id, username }, ...session });
  }

  async function logout(req, res) {
    const { tokenHash } = authenticate(req);
    store.deleteSession(tokenHash);
    await store.save();
    json(res, 200, { ok: true });
  }

  async function changePassword(req, res) {
    const { user, tokenHash } = authenticate(req);
    if (!limiter.hit('pw:' + user.id, 8, WINDOW)) throw new HttpError(429, 'RATE_LIMITED');
    const body = await readJson(req);
    if (typeof body.currentPassword !== 'string' || !(await verifyPassword(body.currentPassword, user.passwordHash))) {
      throw new HttpError(401, 'INVALID_CREDENTIALS');
    }
    if (!validPassword(body.newPassword)) throw new HttpError(400, 'INVALID_PASSWORD');
    user.passwordHash = await hashPassword(body.newPassword);
    store.deleteSessionsForUser(user.id, tokenHash); // sign out all other devices
    await store.save();
    json(res, 200, { ok: true });
  }

  // ---- profile handlers
  const aad = (profile) => profile.userId + ':' + profile.id;

  function requireVault() {
    if (!vaultKey) throw new HttpError(503, 'KEY_STORAGE_DISABLED');
    return vaultKey;
  }

  async function createProfile(req, res) {
    const { user } = authenticate(req);
    const body = await readJson(req);
    if (!validProfileName(body.name)) throw new HttpError(400, 'INVALID_NAME');
    if (!validRtmpServer(body.server)) throw new HttpError(400, 'INVALID_SERVER');
    const hasKey = body.streamKey !== undefined && body.streamKey !== null;
    if (hasKey && !validStreamKey(body.streamKey)) throw new HttpError(400, 'INVALID_STREAM_KEY');
    if (hasKey) requireVault();
    if (store.listProfiles(user.id).length >= MAX_PROFILES) throw new HttpError(409, 'PROFILE_LIMIT');
    const t = now();
    const profile = {
      id: randomUUID(),
      userId: user.id,
      name: body.name.trim(),
      server: body.server.trim(),
      sealedKey: null,
      createdAt: t,
      updatedAt: t
    };
    if (hasKey) profile.sealedKey = seal(vaultKey, body.streamKey.trim(), aad(profile));
    store.putProfile(profile);
    await store.save();
    json(res, 201, publicProfile(profile));
  }

  async function updateProfile(req, res, id) {
    const { user } = authenticate(req);
    const profile = store.getProfile(user.id, id);
    if (!profile) throw new HttpError(404, 'NOT_FOUND');
    const body = await readJson(req);
    if (body.name !== undefined) {
      if (!validProfileName(body.name)) throw new HttpError(400, 'INVALID_NAME');
      profile.name = body.name.trim();
    }
    if (body.server !== undefined) {
      if (!validRtmpServer(body.server)) throw new HttpError(400, 'INVALID_SERVER');
      profile.server = body.server.trim();
    }
    if (body.streamKey === null) {
      profile.sealedKey = null; // explicit removal
    } else if (body.streamKey !== undefined) {
      if (!validStreamKey(body.streamKey)) throw new HttpError(400, 'INVALID_STREAM_KEY');
      profile.sealedKey = seal(requireVault(), body.streamKey.trim(), aad(profile));
    }
    profile.updatedAt = now();
    await store.save();
    json(res, 200, publicProfile(profile));
  }

  async function revealKey(req, res, id) {
    const { user } = authenticate(req);
    if (!limiter.hit('reveal:' + user.id, 30, WINDOW)) throw new HttpError(429, 'RATE_LIMITED');
    const profile = store.getProfile(user.id, id);
    if (!profile) throw new HttpError(404, 'NOT_FOUND');
    if (!profile.sealedKey) throw new HttpError(404, 'NO_KEY_STORED');
    let streamKey;
    try {
      streamKey = open(requireVault(), profile.sealedKey, aad(profile));
    } catch (error) {
      if (error instanceof HttpError) throw error;
      throw new HttpError(500, 'KEY_DECRYPT_FAILED'); // wrong VAULT_KEY or tampered record
    }
    json(res, 200, { id: profile.id, server: profile.server, streamKey });
  }

  async function route(req, res) {
    let path;
    try {
      path = new URL(req.url || '/', 'http://localhost').pathname;
    } catch {
      throw new HttpError(400, 'BAD_URL');
    }
    const method = req.method;

    if (method === 'GET' && path === '/health') {
      return json(res, 200, { service: SERVICE, status: 'ok', version: VERSION });
    }

    if (method === 'GET' && path === '/api/v1/capabilities') {
      return json(res, 200, {
        service: SERVICE,
        tiktokLiveKeyIssuance: false,
        tiktokLogin: false,
        manualRtmpExport: true,
        accounts: true,
        registrationOpen,
        profiles: true,
        encryptedKeyStorage: Boolean(vaultKey),
        note: 'No TikTok integration has been verified or enabled.'
      });
    }

    if (method === 'POST' && path === '/api/v1/sessions') {
      // Deliberately unavailable until a lawful, authorized provider is
      // implemented and tested. Never invent streaming keys.
      req.resume();
      return json(res, 501, {
        error: 'TIKTOK_PROVIDER_NOT_CONFIGURED',
        message: 'No verified TikTok LIVE session provider is installed.'
      });
    }

    if (method === 'POST' && path === '/api/v1/auth/register') return register(req, res);
    if (method === 'POST' && path === '/api/v1/auth/login') return login(req, res);
    if (method === 'POST' && path === '/api/v1/auth/logout') return logout(req, res);
    if (method === 'POST' && path === '/api/v1/auth/password') return changePassword(req, res);

    if (method === 'GET' && path === '/api/v1/me') {
      const { user } = authenticate(req);
      return json(res, 200, { id: user.id, username: user.username });
    }

    if (path === '/api/v1/profiles') {
      if (method === 'GET') {
        const { user } = authenticate(req);
        return json(res, 200, { profiles: store.listProfiles(user.id).map(publicProfile) });
      }
      if (method === 'POST') return createProfile(req, res);
    }

    const match = /^\/api\/v1\/profiles\/([0-9a-f-]{36})(\/reveal-key)?$/.exec(path);
    if (match) {
      const id = match[1];
      if (match[2]) {
        if (method === 'POST') {
          req.resume();
          return revealKey(req, res, id);
        }
      } else if (method === 'GET') {
        const { user } = authenticate(req);
        const profile = store.getProfile(user.id, id);
        if (!profile) throw new HttpError(404, 'NOT_FOUND');
        return json(res, 200, publicProfile(profile));
      } else if (method === 'PUT') {
        return updateProfile(req, res, id);
      } else if (method === 'DELETE') {
        const { user } = authenticate(req);
        if (!store.getProfile(user.id, id)) throw new HttpError(404, 'NOT_FOUND');
        store.deleteProfile(id);
        await store.save();
        return json(res, 200, { ok: true });
      }
    }

    if (method === 'OPTIONS') throw new HttpError(405, 'CORS_NOT_ENABLED');
    throw new HttpError(404, 'NOT_FOUND');
  }

  return http.createServer((req, res) => {
    route(req, res).catch((error) => {
      if (res.headersSent) return res.end();
      if (error instanceof HttpError) {
        if (error.status === 429) res.setHeader('Retry-After', '900');
        return json(res, error.status, { error: error.code });
      }
      return json(res, 500, { error: 'INTERNAL' }); // never echo internals or request bodies
    });
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
    let server;
    try {
      server = createAppServer({
        dataDir: process.env.DATA_DIR || null,
        vaultKey: parseVaultKey(process.env.VAULT_KEY),
        allowRegistration: process.env.ALLOW_REGISTRATION === 'true',
        registrationCode: process.env.REGISTRATION_CODE || null,
        trustProxy: process.env.TRUST_PROXY === 'true',
        sessionDays: Number(process.env.SESSION_DAYS || '30')
      });
    } catch (error) {
      process.stderr.write(error.message + '\n');
      process.exitCode = 1;
    }
    if (server) {
      if (!process.env.DATA_DIR) {
        process.stdout.write('DATA_DIR not set: accounts and profiles live in memory only.\n');
      }
      server.listen(port, host, () => {
        process.stdout.write('Live Key Studio backend listening on ' + host + ':' + port + '\n');
      });
    }
  }
}
