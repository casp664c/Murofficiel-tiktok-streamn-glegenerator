import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createAppServer } from '../src/server.mjs';

async function start(options) {
  const server = createAppServer(options);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port;
  const call = async (method, path, { token, body } = {}) => {
    const headers = {};
    if (token) headers.Authorization = 'Bearer ' + token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const res = await fetch(base + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    return { status: res.status, body: await res.json() };
  };
  const stop = () => new Promise((r, j) => server.close((e) => (e ? j(e) : r())));
  return { call, stop };
}

const PASSWORD = 'correct horse battery';

async function signUp(call, username = 'cas') {
  const res = await call('POST', '/api/v1/auth/register', { body: { username, password: PASSWORD } });
  assert.equal(res.status, 201);
  return res.body.token;
}

test('registration is closed by default', async () => {
  const { call, stop } = await start({});
  const res = await call('POST', '/api/v1/auth/register', { body: { username: 'cas', password: PASSWORD } });
  assert.equal(res.status, 403);
  assert.equal(res.body.error, 'REGISTRATION_CLOSED');
  await stop();
});

test('invite code gates registration', async () => {
  const { call, stop } = await start({ registrationCode: 'letmein-123' });
  const bad = await call('POST', '/api/v1/auth/register', {
    body: { username: 'cas', password: PASSWORD, inviteCode: 'nope' }
  });
  assert.equal(bad.status, 403);
  const ok = await call('POST', '/api/v1/auth/register', {
    body: { username: 'cas', password: PASSWORD, inviteCode: 'letmein-123' }
  });
  assert.equal(ok.status, 201);
  await stop();
});

test('register, login, me, logout', async () => {
  const { call, stop } = await start({ allowRegistration: true });
  const token = await signUp(call, 'Cas');
  assert.equal((await call('GET', '/api/v1/me', { token })).body.username, 'cas');

  const dup = await call('POST', '/api/v1/auth/register', { body: { username: 'cas', password: PASSWORD } });
  assert.equal(dup.status, 409);

  const login = await call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: PASSWORD } });
  assert.equal(login.status, 200);
  assert.ok(login.body.token && login.body.token !== token);

  assert.equal((await call('POST', '/api/v1/auth/logout', { token: login.body.token })).status, 200);
  assert.equal((await call('GET', '/api/v1/me', { token: login.body.token })).status, 401);
  assert.equal((await call('GET', '/api/v1/me', { token })).status, 200);
  await stop();
});

test('weak input and wrong credentials are rejected without leaking which part failed', async () => {
  const { call, stop } = await start({ allowRegistration: true });
  assert.equal((await call('POST', '/api/v1/auth/register', { body: { username: 'x', password: PASSWORD } })).status, 400);
  assert.equal((await call('POST', '/api/v1/auth/register', { body: { username: 'cas', password: 'short' } })).status, 400);
  await signUp(call);
  const wrongPw = await call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: 'wrong password!!' } });
  const noUser = await call('POST', '/api/v1/auth/login', { body: { username: 'ghost', password: 'wrong password!!' } });
  assert.equal(wrongPw.status, 401);
  assert.equal(noUser.status, 401);
  assert.deepEqual(wrongPw.body, noUser.body);
  await stop();
});

test('repeated failed logins are rate limited', async () => {
  const { call, stop } = await start({ allowRegistration: true });
  await signUp(call);
  let last;
  for (let i = 0; i < 9; i++) {
    last = await call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: 'wrong password!!' } });
  }
  assert.equal(last.status, 429);
  await stop();
});

test('sessions expire', async () => {
  let t = 1_000_000;
  const { call, stop } = await start({ allowRegistration: true, sessionDays: 1, now: () => t });
  const token = await signUp(call);
  assert.equal((await call('GET', '/api/v1/me', { token })).status, 200);
  t += 2 * 24 * 60 * 60 * 1000;
  assert.equal((await call('GET', '/api/v1/me', { token })).status, 401);
  await stop();
});

test('changing password signs out other sessions', async () => {
  const { call, stop } = await start({ allowRegistration: true });
  const first = await signUp(call);
  const second = (await call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: PASSWORD } })).body.token;
  const changed = await call('POST', '/api/v1/auth/password', {
    token: second,
    body: { currentPassword: PASSWORD, newPassword: 'a brand new passphrase' }
  });
  assert.equal(changed.status, 200);
  assert.equal((await call('GET', '/api/v1/me', { token: first })).status, 401);
  assert.equal((await call('GET', '/api/v1/me', { token: second })).status, 200);
  const relog = await call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: 'a brand new passphrase' } });
  assert.equal(relog.status, 200);
  await stop();
});

test('profiles require login and validate input', async () => {
  const { call, stop } = await start({ allowRegistration: true, vaultKey: randomBytes(32) });
  assert.equal((await call('GET', '/api/v1/profiles')).status, 401);
  const token = await signUp(call);
  const bad = await call('POST', '/api/v1/profiles', {
    token,
    body: { name: 'Min stream', server: 'http://example.com/app' }
  });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error, 'INVALID_SERVER');
  const created = await call('POST', '/api/v1/profiles', {
    token,
    body: { name: 'Min stream', server: 'rtmps://live.example.com/app', streamKey: 'abc-123' }
  });
  assert.equal(created.status, 201);
  assert.equal(created.body.hasKey, true);
  assert.equal(created.body.streamKey, undefined, 'the key is never returned by normal endpoints');
  const list = await call('GET', '/api/v1/profiles', { token });
  assert.equal(list.body.profiles.length, 1);
  assert.equal(JSON.stringify(list.body).includes('abc-123'), false);
  await stop();
});

test('stream keys are encrypted at rest and revealed only on request', async () => {
  const dir = mkdtempSync(join(tmpdir(), 'lks-'));
  try {
    const vaultKey = randomBytes(32);
    const { call, stop } = await start({ allowRegistration: true, vaultKey, dataDir: dir });
    const token = await signUp(call);
    const created = await call('POST', '/api/v1/profiles', {
      token,
      body: { name: 'A', server: 'rtmp://live.example.com/app', streamKey: 'super-secret-key-value' }
    });
    const reveal = await call('POST', '/api/v1/profiles/' + created.body.id + '/reveal-key', { token });
    assert.equal(reveal.status, 200);
    assert.equal(reveal.body.streamKey, 'super-secret-key-value');
    await stop();

    const onDisk = readFileSync(join(dir, 'store.json'), 'utf8');
    assert.equal(onDisk.includes('super-secret-key-value'), false);
    assert.equal(onDisk.includes(PASSWORD), false);
    assert.equal(onDisk.includes(token), false, 'bearer tokens are stored hashed');

    // Restart with the same data and key: everything survives.
    const again = await start({ allowRegistration: true, vaultKey, dataDir: dir });
    const login = await again.call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: PASSWORD } });
    const revealed = await again.call('POST', '/api/v1/profiles/' + created.body.id + '/reveal-key', {
      token: login.body.token
    });
    assert.equal(revealed.body.streamKey, 'super-secret-key-value');
    await again.stop();

    // Restart with the wrong key: decryption fails cleanly.
    const wrong = await start({ allowRegistration: true, vaultKey: randomBytes(32), dataDir: dir });
    const login2 = await wrong.call('POST', '/api/v1/auth/login', { body: { username: 'cas', password: PASSWORD } });
    const failed = await wrong.call('POST', '/api/v1/profiles/' + created.body.id + '/reveal-key', {
      token: login2.body.token
    });
    assert.equal(failed.status, 500);
    assert.equal(failed.body.error, 'KEY_DECRYPT_FAILED');
    await wrong.stop();
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('storing a key is refused when no vault key is configured', async () => {
  const { call, stop } = await start({ allowRegistration: true });
  const token = await signUp(call);
  const res = await call('POST', '/api/v1/profiles', {
    token,
    body: { name: 'A', server: 'rtmps://live.example.com/app', streamKey: 'abc' }
  });
  assert.equal(res.status, 503);
  assert.equal(res.body.error, 'KEY_STORAGE_DISABLED');
  const noKey = await call('POST', '/api/v1/profiles', {
    token,
    body: { name: 'A', server: 'rtmps://live.example.com/app' }
  });
  assert.equal(noKey.status, 201);
  assert.equal(noKey.body.hasKey, false);
  await stop();
});

test('profiles are private per user, and can be updated and deleted', async () => {
  const { call, stop } = await start({ allowRegistration: true, vaultKey: randomBytes(32) });
  const alice = await signUp(call, 'alice');
  const bob = await signUp(call, 'bob');
  const made = await call('POST', '/api/v1/profiles', {
    token: alice,
    body: { name: 'Alice', server: 'rtmps://live.example.com/app', streamKey: 'k1' }
  });
  const id = made.body.id;
  assert.equal((await call('GET', '/api/v1/profiles/' + id, { token: bob })).status, 404);
  assert.equal((await call('POST', '/api/v1/profiles/' + id + '/reveal-key', { token: bob })).status, 404);
  assert.equal((await call('DELETE', '/api/v1/profiles/' + id, { token: bob })).status, 404);
  assert.equal((await call('GET', '/api/v1/profiles', { token: bob })).body.profiles.length, 0);

  const upd = await call('PUT', '/api/v1/profiles/' + id, {
    token: alice,
    body: { name: 'Alice 2', streamKey: null }
  });
  assert.equal(upd.status, 200);
  assert.equal(upd.body.name, 'Alice 2');
  assert.equal(upd.body.hasKey, false);
  assert.equal((await call('POST', '/api/v1/profiles/' + id + '/reveal-key', { token: alice })).status, 404);

  assert.equal((await call('DELETE', '/api/v1/profiles/' + id, { token: alice })).status, 200);
  assert.equal((await call('GET', '/api/v1/profiles/' + id, { token: alice })).status, 404);
  await stop();
});

test('non-JSON and oversized bodies are rejected', async () => {
  const { stop } = await start({ allowRegistration: true });
  await stop();
  const server = createAppServer({ allowRegistration: true });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = 'http://127.0.0.1:' + server.address().port;
  const plain = await fetch(base + '/api/v1/auth/login', { method: 'POST', body: 'hello' });
  assert.equal(plain.status, 415);
  const huge = await fetch(base + '/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'x'.repeat(20_000) })
  });
  assert.equal(huge.status, 413);
  const broken = await fetch(base + '/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{nope'
  });
  assert.equal(broken.status, 400);
  await new Promise((r) => server.close(r));
});
