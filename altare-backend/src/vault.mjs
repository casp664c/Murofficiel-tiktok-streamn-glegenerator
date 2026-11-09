import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';

/** Parses a base64 32-byte key (e.g. `openssl rand -base64 32`). Returns null if unset. */
export function parseVaultKey(value) {
  if (!value) return null;
  const key = Buffer.from(value, 'base64');
  if (key.length !== 32) {
    throw new Error('VAULT_KEY must be 32 bytes encoded as base64');
  }
  return key;
}

/** AES-256-GCM. `aad` binds the ciphertext to its owner so records cannot be swapped. */
export function seal(key, plaintext, aad) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(aad));
  const ct = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return ['v1', iv.toString('base64'), tag.toString('base64'), ct.toString('base64')].join('.');
}

export function open(key, sealed, aad) {
  const parts = String(sealed).split('.');
  if (parts.length !== 4 || parts[0] !== 'v1') throw new Error('BAD_SEALED_VALUE');
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(parts[1], 'base64'));
  decipher.setAAD(Buffer.from(aad));
  decipher.setAuthTag(Buffer.from(parts[2], 'base64'));
  return Buffer.concat([
    decipher.update(Buffer.from(parts[3], 'base64')),
    decipher.final()
  ]).toString('utf8');
}
