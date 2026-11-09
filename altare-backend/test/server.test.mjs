import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import { createAppServer } from '../src/server.mjs';

const server = createAppServer();
let url;

before(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  url = 'http://127.0.0.1:' + server.address().port;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((err) => err ? reject(err) : resolve()));
});

test('health reports real backend status', async () => {
  const response = await fetch(url + '/health');
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  const body = await response.json();
  assert.equal(body.service, 'live-key-studio-backend');
  assert.equal(body.status, 'ok');
});

test('capabilities do not claim TikTok integration', async () => {
  const response = await fetch(url + '/api/v1/capabilities');
  assert.equal(response.status, 200);
  const capabilities = await response.json();
  assert.equal(capabilities.tiktokLiveKeyIssuance, false);
  assert.equal(capabilities.tiktokLogin, false);
  assert.equal(capabilities.manualRtmpExport, true);
});

test('unavailable generator never fabricates a stream key', async () => {
  const response = await fetch(url + '/api/v1/sessions', { method: 'POST' });
  assert.equal(response.status, 501);
  const body = await response.json();
  assert.equal(body.error, 'TIKTOK_PROVIDER_NOT_CONFIGURED');
  assert.equal(body.streamKey, undefined);
});

test('unexpected routes return 404 without CORS', async () => {
  const response = await fetch(url + '/somewhere');
  assert.equal(response.status, 404);
  assert.equal(response.headers.get('access-control-allow-origin'), null);
});
