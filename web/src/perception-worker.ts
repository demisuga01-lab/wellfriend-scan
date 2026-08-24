import type { WebPerceptionEngine } from "./perception/contracts.js";
import type { RuntimeImage } from "./perception/worker-engine.js";
import type { WorkerRequest, WorkerResponse } from "./workers/protocol.js";

let configuredRuntime: WebPerceptionEngine | undefined;
let configuredLoader: (() => Promise<WebPerceptionEngine>) | undefined;
let configuredImageRegistrar: ((uri: string, image: RuntimeImage) => void) | undefined;

/** Production worker entry points configure this loader; tests pass their engine explicitly. */
export function configureProductionWorker(loader: () => Promise<WebPerceptionEngine>): void { configuredLoader = loader; configuredRuntime = undefined; }
export function configureWorkerImageRegistrar(registrar: (uri: string, image: RuntimeImage) => void): void { configuredImageRegistrar = registrar; }
async function runtime(): Promise<WebPerceptionEngine> {
  if (configuredRuntime) return configuredRuntime;
  if (!configuredLoader) throw new Error("reviewed Wellfriend WASM runtime is not configured; production perception fails closed");
  configuredRuntime = await configuredLoader();
  if (configuredRuntime.mode !== "WASM") throw new Error("production worker rejected a non-WASM perception engine");
  return configuredRuntime;
}

/**
 * Worker dispatcher. Dev mocks are accepted only when explicitly supplied by a test/dev caller;
 * browser production entry points load a reviewed `WasmPerceptionEngine` and otherwise fail closed.
 */
export async function handleWorkerRequest(request: WorkerRequest, explicitEngine?: WebPerceptionEngine): Promise<WorkerResponse> {
  try {
    if (request.type === "PING") return { requestId: request.requestId, type: "PONG" };
    if (request.type === "REGISTER_IMAGE") {
      if (!configuredImageRegistrar) return { requestId: request.requestId, type: "ERROR", message: "runtime image registration is not configured" };
      configuredImageRegistrar(request.uri, request.image);
      return { requestId: request.requestId, type: "IMAGE_REGISTERED" };
    }
    if (request.type === "LOAD_WASM") {
      try {
        const engine = explicitEngine ?? await runtime();
        return engine.mode === "WASM" ? { requestId: request.requestId, type: "ENGINE_READY" } : { requestId: request.requestId, type: "ENGINE_FAILED", message: "reviewed Wellfriend WASM runtime is not loaded; production perception fails closed" };
      } catch (error) { return { requestId: request.requestId, type: "ENGINE_FAILED", message: error instanceof Error ? error.message : "WASM runtime load failed" }; }
    }
    const engine = explicitEngine ?? await runtime();
    if (request.type === "CANCEL_JOB") return { requestId: request.requestId, type: "ERROR", message: "cancellation is acknowledged but no job queue is active" };
    if (request.type === "ANALYZE_FRAME") return { requestId: request.requestId, type: "ANALYSIS_RESULT", result: await engine.analyzeFrame(request.frame) };
    if (request.type === "RECONSTRUCT_PAGE") return { requestId: request.requestId, type: "RECONSTRUCTION_RESULT", result: await engine.reconstructPage(request.request) };
    if (request.type === "APPLY_FILTER") return { requestId: request.requestId, type: "FILTER_RESULT", result: await engine.applyFilter(request.request) };
    return { requestId: request.requestId, type: "ERROR", message: "unsupported worker request" };
  } catch (error) { return { requestId: request.requestId, type: "ERROR", message: error instanceof Error ? error.message : "worker request failed" }; }
}
