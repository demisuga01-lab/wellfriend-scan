/** Small lifecycle helper so session metadata never owns unbounded browser object URLs. */
export class ObjectUrlRegistry {
  private readonly urls = new Map<string, string>();
  constructor(private readonly revoke: (url: string) => void = (url) => URL.revokeObjectURL(url)) {}
  track(id: string, url: string): void { this.release(id); this.urls.set(id, url); }
  release(id: string): void { const url = this.urls.get(id); if (url) { this.revoke(url); this.urls.delete(id); } }
  dispose(): void { [...this.urls.keys()].forEach((id) => this.release(id)); }
}
