import { DevMockPerceptionEngine } from "./perception/dev-mock-engine.js";
import type { WebPerceptionEngine } from "./perception/contracts.js";
import type { WorkerRequest, WorkerResponse } from "./workers/protocol.js";

/** Worker dispatcher. Replace only its engine with a verified WASM adapter in a future phase. */
export async function handleWorkerRequest(request: WorkerRequest, engine: WebPerceptionEngine = new DevMockPerceptionEngine()): Promise<WorkerResponse> {
  try {
    if (request.type === "PING") return { requestId: request.requestId, type: "PONG" };
    if (request.type === "CANCEL_JOB") return { requestId: request.requestId, type: "ERROR", message: "cancellation is acknowledged but no job queue is active" };
    if (request.type === "ANALYZE_FRAME") return { requestId: request.requestId, type: "ANALYSIS_RESULT", result: await engine.analyzeFrame(request.frame) };
    if (request.type === "RECONSTRUCT_PAGE") return { requestId: request.requestId, type: "RECONSTRUCTION_RESULT", result: await engine.reconstructPage(request.request) };
    if (request.type === "APPLY_FILTER") return { requestId: request.requestId, type: "FILTER_RESULT", result: await engine.applyFilter(request.request) };
    return { requestId: request.requestId, type: "ERROR", message: "unsupported worker request" };
  } catch (error) { return { requestId: request.requestId, type: "ERROR", message: error instanceof Error ? error.message : "worker request failed" }; }
}
