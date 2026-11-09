/** Small fixed-window in-memory limiter. Resets on restart, which is fine for one instance. */
export class RateLimiter {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.buckets = new Map();
  }

  /** Returns true if the action is allowed, false if the limit was exceeded. */
  hit(key, limit, windowMs) {
    const t = this.now();
    if (this.buckets.size > 5000) this.sweep(t);
    const bucket = this.buckets.get(key);
    if (!bucket || bucket.resetAt <= t) {
      this.buckets.set(key, { count: 1, resetAt: t + windowMs });
      return true;
    }
    bucket.count += 1;
    return bucket.count <= limit;
  }

  reset(key) {
    this.buckets.delete(key);
  }

  sweep(t = this.now()) {
    for (const [key, bucket] of this.buckets) {
      if (bucket.resetAt <= t) this.buckets.delete(key);
    }
  }
}
