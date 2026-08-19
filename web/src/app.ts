import { ScannerShell } from "./components/scanner-shell.js";
import { DevMockPerceptionEngine } from "./perception/dev-mock-engine.js";
import { WorkerPerceptionEngine } from "./perception/worker-engine.js";
import type { WebPerceptionEngine } from "./perception/contracts.js";
import { WebScanController } from "./scanner/controller.js";

const root = document.querySelector<HTMLElement>("#app");
if (!root) throw new Error("web scanner root is missing");
// The worker currently hosts a dev-only mock; production must use a verified WASM adapter instead.
const engine: WebPerceptionEngine = typeof Worker !== "undefined" ? new WorkerPerceptionEngine(new Worker("/web/src/worker-entry.js", { type: "module" })) : new DevMockPerceptionEngine();
const shell = new ScannerShell(root, new WebScanController(engine));
window.addEventListener("pagehide", () => { shell.dispose(); engine.dispose?.(); });
