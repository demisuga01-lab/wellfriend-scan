import { configureProductionWorker, handleWorkerRequest } from "./perception-worker.js";
import { WasmPerceptionEngine } from "./perception/worker-engine.js";
import { loadVerifiedWasmRuntime } from "./perception/wasm-artifact-loader.js";
import type { WorkerRequest } from "./workers/protocol.js";

configureProductionWorker(async () => WasmPerceptionEngine.fromModule(await loadVerifiedWasmRuntime("/wasm")));
self.onmessage = (event: MessageEvent<WorkerRequest>) => { void handleWorkerRequest(event.data).then((response) => self.postMessage(response)); };
