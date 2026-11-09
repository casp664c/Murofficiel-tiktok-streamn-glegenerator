// Input validation shared by the HTTP layer. Mirrors the Android StreamSettings rules.

const CONTROL = /[\u0000-\u001f\u007f]/;

export function validRtmpServer(raw) {
  if (typeof raw !== 'string') return false;
  const value = raw.trim();
  if (value.length === 0 || value.length > 512 || CONTROL.test(value)) return false;
  let url;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  return (
    (url.protocol === 'rtmp:' || url.protocol === 'rtmps:') &&
    url.hostname.length > 0 &&
    url.username === '' &&
    url.password === '' &&
    url.hash === ''
  );
}

export function validStreamKey(raw) {
  return (
    typeof raw === 'string' &&
    raw.trim().length > 0 &&
    raw.trim().length <= 512 &&
    !CONTROL.test(raw.trim())
  );
}

export function normalizeUsername(raw) {
  if (typeof raw !== 'string') return null;
  const value = raw.trim().toLowerCase();
  return /^[a-z0-9_.-]{3,32}$/.test(value) ? value : null;
}

export function validPassword(raw) {
  return typeof raw === 'string' && raw.length >= 10 && raw.length <= 128;
}

export function validProfileName(raw) {
  if (typeof raw !== 'string') return false;
  const value = raw.trim();
  return value.length >= 1 && value.length <= 60 && !CONTROL.test(value);
}
