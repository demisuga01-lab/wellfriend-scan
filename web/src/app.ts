import { ScannerShell } from "./components/scanner-shell.js";
import { WorkerPerceptionEngine } from "./perception/worker-engine.js";
import type { WebPerceptionEngine } from "./perception/contracts.js";
import { WebScanController } from "./scanner/controller.js";

const root = document.querySelector<HTMLElement>("#app");
if (!root) throw new Error("web scanner root is missing");
class UnavailableWebPerceptionEngine implements WebPerceptionEngine {
  readonly mode = "WORKER" as const;
  private unavailable(): never { throw new Error("browser Worker/WASM runtime is unavailable; production scanner fails closed"); }
  async analyzeFrame() { return this.unavailable(); }
  async reconstructPage() { return this.unavailable(); }
  async applyFilter() { return this.unavailable(); }
}
// Production always uses the verified worker/WASM path. Dev mocks are injected only by tests.
const engine: WebPerceptionEngine = typeof Worker !== "undefined" ? new WorkerPerceptionEngine(new Worker("/web/src/worker-entry.js", { type: "module" })) : new UnavailableWebPerceptionEngine();
if (engine instanceof WorkerPerceptionEngine) void engine.loadWasm().catch(() => { /* explicit worker ENGINE_FAILED state is surfaced on request */ });
const shell = new ScannerShell(root, new WebScanController(engine));
window.addEventListener("pagehide", () => { shell.dispose(); engine.dispose?.(); });
