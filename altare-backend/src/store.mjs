import { mkdirSync, readFileSync, existsSync } from 'node:fs';
import { rename, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Tiny JSON-file store. With no directory it is purely in-memory (used by tests).
 * Writes are serialised and atomic (temp file + rename), file mode 0600.
 */
export class Store {
  constructor(dir = null) {
    this.dir = dir;
    this.file = dir ? join(dir, 'store.json') : null;
    this.data = { users: {}, sessions: {}, profiles: {} };
    this.writing = Promise.resolve();
    if (dir) {
      mkdirSync(dir, { recursive: true, mode: 0o700 });
      if (existsSync(this.file)) {
        const loaded = JSON.parse(readFileSync(this.file, 'utf8'));
        this.data = {
          users: loaded.users || {},
          sessions: loaded.sessions || {},
          profiles: loaded.profiles || {}
        };
      }
    }
  }

  save() {
    if (!this.file) return Promise.resolve();
    const snapshot = JSON.stringify(this.data);
    this.writing = this.writing.then(async () => {
      const tmp = this.file + '.tmp';
      await writeFile(tmp, snapshot, { mode: 0o600 });
      await rename(tmp, this.file);
    });
    return this.writing;
  }

  // ---- users
  findUserByName(username) {
    return Object.values(this.data.users).find((u) => u.username === username) || null;
  }

  addUser(user) {
    this.data.users[user.id] = user;
  }

  getUser(id) {
    return this.data.users[id] || null;
  }

  // ---- sessions (keyed by SHA-256 of the bearer token)
  addSession(tokenHash, session) {
    this.data.sessions[tokenHash] = session;
  }

  getSession(tokenHash, now) {
    const session = this.data.sessions[tokenHash];
    if (!session) return null;
    if (session.expiresAt <= now) {
      delete this.data.sessions[tokenHash];
      return null;
    }
    return session;
  }

  deleteSession(tokenHash) {
    delete this.data.sessions[tokenHash];
  }

  deleteSessionsForUser(userId, exceptHash = null) {
    for (const [hash, session] of Object.entries(this.data.sessions)) {
      if (session.userId === userId && hash !== exceptHash) delete this.data.sessions[hash];
    }
  }

  // ---- profiles
  listProfiles(userId) {
    return Object.values(this.data.profiles)
      .filter((p) => p.userId === userId)
      .sort((a, b) => a.createdAt - b.createdAt);
  }

  getProfile(userId, id) {
    const profile = this.data.profiles[id];
    return profile && profile.userId === userId ? profile : null;
  }

  putProfile(profile) {
    this.data.profiles[profile.id] = profile;
  }

  deleteProfile(id) {
    delete this.data.profiles[id];
  }
}
