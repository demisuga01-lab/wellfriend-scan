import type { FilterRequest, FilterResult, FrameAnalysisResult, PerceptionFrame, ReconstructionRequest, ReconstructionResult, WebPerceptionEngine } from "./contracts.js";
import type { WorkerRequest, WorkerResponse } from "../workers/protocol.js";

/** Browser-worker transport adapter. It moves bytes off the UI thread without owning algorithms. */
export class WorkerPerceptionEngine implements WebPerceptionEngine {
  readonly mode = "WORKER" as const;
  private nextId = 0;
  private readonly pending = new Map<string, { resolve: (value: WorkerResponse) => void; reject: (reason: Error) => void }>();
  constructor(private readonly worker: Worker) { worker.onmessage = (event: MessageEvent<WorkerResponse>) => { const pending = this.pending.get(event.data.requestId); if (!pending) return; this.pending.delete(event.data.requestId); pending.resolve(event.data); }; worker.onerror = (event) => { this.rejectAll(new Error(event.message || "perception worker failed")); }; }
  private rejectAll(error: Error): void { this.pending.forEach(({ reject }) => reject(error)); this.pending.clear(); }
  private async send(request: { readonly type: "ANALYZE_FRAME"; readonly frame: PerceptionFrame } | { readonly type: "RECONSTRUCT_PAGE"; readonly request: ReconstructionRequest } | { readonly type: "APPLY_FILTER"; readonly request: FilterRequest }): Promise<WorkerResponse> { const requestId = `worker-${++this.nextId}`; const complete = { ...request, requestId } as WorkerRequest; const result = new Promise<WorkerResponse>((resolve, reject) => this.pending.set(requestId, { resolve, reject })); this.worker.postMessage(complete); const response = await result; if (response.type === "ERROR") throw new Error(response.message); return response; }
  async analyzeFrame(frame: PerceptionFrame): Promise<FrameAnalysisResult> { const response = await this.send({ type: "ANALYZE_FRAME", frame }); if (response.type !== "ANALYSIS_RESULT") throw new Error("unexpected worker analysis response"); return response.result; }
  async reconstructPage(request: ReconstructionRequest): Promise<ReconstructionResult> { const response = await this.send({ type: "RECONSTRUCT_PAGE", request }); if (response.type !== "RECONSTRUCTION_RESULT") throw new Error("unexpected worker reconstruction response"); return response.result; }
  async applyFilter(request: FilterRequest): Promise<FilterResult> { const response = await this.send({ type: "APPLY_FILTER", request }); if (response.type !== "FILTER_RESULT") throw new Error("unexpected worker filter response"); return response.result; }
  dispose(): void { this.rejectAll(new Error("perception worker disposed")); this.worker.terminate(); }
}

/** Explicit future WASM boundary. It fails closed when no reviewed WASM module is supplied. */
export class WasmPerceptionEngine implements WebPerceptionEngine {
  readonly mode = "WASM" as const;
  private unavailable(): never { throw new Error("wellfriend-perception WASM artifact is not published; production web perception is unavailable"); }
  async analyzeFrame(_frame: PerceptionFrame): Promise<FrameAnalysisResult> { return this.unavailable(); }
  async reconstructPage(_request: ReconstructionRequest): Promise<ReconstructionResult> { return this.unavailable(); }
  async applyFilter(_request: FilterRequest): Promise<FilterResult> { return this.unavailable(); }
}
