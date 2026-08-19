import { configureProductionWorker, configureWorkerImageRegistrar, handleWorkerRequest } from "./perception-worker.js";
import { WasmPerceptionEngine, WorkerRuntimeImageStore } from "./perception/worker-engine.js";
import { loadVerifiedWasmRuntime } from "./perception/wasm-artifact-loader.js";
import type { WorkerRequest } from "./workers/protocol.js";

const runtimeImages = new WorkerRuntimeImageStore();
configureWorkerImageRegistrar((uri, image) => runtimeImages.register(uri, image));
configureProductionWorker(async () => WasmPerceptionEngine.fromModule(await loadVerifiedWasmRuntime("/wasm"), runtimeImages));
self.onmessage = (event: MessageEvent<WorkerRequest>) => { void handleWorkerRequest(event.data).then((response) => self.postMessage(response)); };
