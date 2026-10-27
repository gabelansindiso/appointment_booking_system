export class IdempotencyKeys {
  private last?: { fingerprint: string; key: string };

  keyFor(payload: unknown): string {
    const fingerprint = JSON.stringify(payload);
    if (this.last?.fingerprint !== fingerprint) {
      this.last = { fingerprint, key: crypto.randomUUID() };
    }
    return this.last.key;
  }

  reset(): void {
    this.last = undefined;
  }
}
