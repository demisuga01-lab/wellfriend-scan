import type { FilterRequest, FilterResult, FrameAnalysisResult, PerceptionFrame, ReconstructionRequest, ReconstructionResult, WebPerceptionEngine } from "./contracts.js";
import type { WorkerRequest, WorkerResponse } from "../workers/protocol.js";
import type { CaptureGuidance, CaptureReadiness, PageGeometry } from "../../../shared/src/scan-session.js";

type WorkerRequestPayload =
  | { readonly type: "ANALYZE_FRAME"; readonly frame: PerceptionFrame }
  | { readonly type: "RECONSTRUCT_PAGE"; readonly request: ReconstructionRequest }
  | { readonly type: "APPLY_FILTER"; readonly request: FilterRequest }
  | { readonly type: "CANCEL_JOB" | "PING" | "LOAD_WASM" };

/** Browser-worker transport adapter. It moves bytes off the UI thread without owning algorithms. */
export class WorkerPerceptionEngine implements WebPerceptionEngine {
  readonly mode = "WORKER" as const;
  private nextId = 0;
  private readonly pending = new Map<string, { resolve: (value: WorkerResponse) => void; reject: (reason: Error) => void }>();
  constructor(private readonly worker: Worker) { worker.onmessage = (event: MessageEvent<WorkerResponse>) => { const pending = this.pending.get(event.data.requestId); if (!pending) return; this.pending.delete(event.data.requestId); pending.resolve(event.data); }; worker.onerror = (event) => { this.rejectAll(new Error(event.message || "perception worker failed")); }; }
  private rejectAll(error: Error): void { this.pending.forEach(({ reject }) => reject(error)); this.pending.clear(); }
  private async send(request: WorkerRequestPayload): Promise<WorkerResponse> { const requestId = `worker-${++this.nextId}`; const complete = { ...request, requestId } as WorkerRequest; const result = new Promise<WorkerResponse>((resolve, reject) => this.pending.set(requestId, { resolve, reject })); this.worker.postMessage(complete); const response = await result; if (response.type === "ERROR" || response.type === "ENGINE_FAILED") throw new Error(response.message); return response; }
  async analyzeFrame(frame: PerceptionFrame): Promise<FrameAnalysisResult> { const response = await this.send({ type: "ANALYZE_FRAME", frame }); if (response.type !== "ANALYSIS_RESULT") throw new Error("unexpected worker analysis response"); return response.result; }
  async reconstructPage(request: ReconstructionRequest): Promise<ReconstructionResult> { const response = await this.send({ type: "RECONSTRUCT_PAGE", request }); if (response.type !== "RECONSTRUCTION_RESULT") throw new Error("unexpected worker reconstruction response"); return response.result; }
  async applyFilter(request: FilterRequest): Promise<FilterResult> { const response = await this.send({ type: "APPLY_FILTER", request }); if (response.type !== "FILTER_RESULT") throw new Error("unexpected worker filter response"); return response.result; }
  async loadWasm(): Promise<void> { const response = await this.send({ type: "LOAD_WASM" }); if (response.type !== "ENGINE_READY") throw new Error("unexpected worker WASM load response"); }
  dispose(): void { this.rejectAll(new Error("perception worker disposed")); this.worker.terminate(); }
}

/** Generated WASM package shape. No TypeScript detector, warp, or filter is permitted here. */
export interface WasmRuntimeHandle {
  analyzeFrame(image: Uint8Array, width: number, height: number, stride: number, pixelFormat: string, requestJson: string): string;
  reconstructPage(image: Uint8Array, width: number, height: number, stride: number, pixelFormat: string, requestJson: string): string;
  applyFilter(image: Uint8Array, width: number, height: number, stride: number, pixelFormat: string, requestJson: string): string;
}
export interface WasmRuntimeModule { createEngine(configJson?: string): WasmRuntimeHandle; }
export interface RuntimeImage { readonly width: number; readonly height: number; readonly stride: number; readonly pixelFormat: string; readonly bytes: Uint8Array; }
/** Resolves original/canonical pixels by URI; UI owns storage, Rust owns algorithms. */
export interface RuntimeImageResolver { resolve(uri: string, declaredSize: { readonly width: number; readonly height: number }): Promise<RuntimeImage>; }

/** Real WASM adapter. It fails closed when no reviewed package or source image resolver is supplied. */
export class WasmPerceptionEngine implements WebPerceptionEngine {
  readonly mode = "WASM" as const;
  private readonly outputs = new Map<string, RuntimeImage>();
  private outputCounter = 0;
  constructor(private readonly handle: WasmRuntimeHandle, private readonly resolver?: RuntimeImageResolver) {}
  static fromModule(module: WasmRuntimeModule, resolver?: RuntimeImageResolver): WasmPerceptionEngine { return new WasmPerceptionEngine(module.createEngine("{}"), resolver); }
  async analyzeFrame(frame: PerceptionFrame): Promise<FrameAnalysisResult> {
    const bytes = new Uint8Array(frame.bytes);
    const { pixelFormat, stride } = inferRawPixelFormat(bytes, frame.size.width, frame.size.height);
    return mapAnalysis(JSON.parse(this.handle.analyzeFrame(bytes, frame.size.width, frame.size.height, stride, pixelFormat, JSON.stringify({ frame_index: Number(frame.frameId) || 0 }))) as unknown, frame);
  }
  async reconstructPage(request: ReconstructionRequest): Promise<ReconstructionResult> {
    const image = await this.requireResolver().resolve(request.sourceUri, request.sourceSize);
    const response = parseRecord(JSON.parse(this.handle.reconstructPage(image.bytes, image.width, image.height, image.stride, image.pixelFormat, JSON.stringify({ quad: { points: request.geometry.corners }, output_long_edge: request.outputLongEdge, aspect_policy: request.aspectPolicy, orientation_policy: request.orientationPolicy, crop_margin_policy: request.cropMarginPolicy }))) as unknown);
    const output = parseRuntimeImage(response.image); const uri = this.store(output);
    return { outputUri: uri, outputSize: { width: output.width, height: output.height }, diagnostics: stringArray(response.diagnostics), confidence: numberValue(response.confidence), engineMode: this.mode };
  }
  async applyFilter(request: FilterRequest): Promise<FilterResult> {
    const image = await this.requireResolver().resolve(request.inputUri, { width: 1, height: 1 });
    const response = parseRecord(JSON.parse(this.handle.applyFilter(image.bytes, image.width, image.height, image.stride, image.pixelFormat, JSON.stringify({ preset: filterName(request.preset) }))) as unknown);
    const output = parseRuntimeImage(response.image); const uri = this.store(output);
    return { outputUri: uri, appliedProcessorIds: stringArray(response.applied_processor_ids), diagnostics: stringArray(response.diagnostics), engineMode: this.mode };
  }
  output(uri: string): RuntimeImage | undefined { return this.outputs.get(uri); }
  private requireResolver(): RuntimeImageResolver { if (!this.resolver) throw new Error("WASM runtime requires a reviewed source-image resolver; no mock fallback is allowed"); return this.resolver; }
  private store(image: RuntimeImage): string { const uri = `wellfriend-runtime://${++this.outputCounter}`; this.outputs.set(uri, image); return uri; }
}

function inferRawPixelFormat(bytes: Uint8Array, width: number, height: number): { pixelFormat: string; stride: number } {
  const pixels = width * height;
  if (bytes.byteLength === pixels) return { pixelFormat: "Gray8", stride: width };
  if (bytes.byteLength === pixels * 3) return { pixelFormat: "Rgb8", stride: width * 3 };
  if (bytes.byteLength === pixels * 4) return { pixelFormat: "Rgba8", stride: width * 4 };
  throw new Error("WASM scalar runtime accepts decoded Gray8/Rgb8/Rgba8 pixels only; encoded camera/image bytes require a decoder");
}
function parseRecord(value: unknown): Record<string, unknown> { if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("invalid Wellfriend runtime JSON object"); return value as Record<string, unknown>; }
function numberValue(value: unknown): number { if (typeof value !== "number" || !Number.isFinite(value)) throw new Error("invalid Wellfriend runtime numeric field"); return value; }
function stringArray(value: unknown): string[] { if (!Array.isArray(value) || value.some((item) => typeof item !== "string")) throw new Error("invalid Wellfriend runtime string array"); return [...value] as string[]; }
function parseRuntimeImage(value: unknown): RuntimeImage { const object = parseRecord(value); const bytes = object.bytes; if (!Array.isArray(bytes) || bytes.some((item) => !Number.isInteger(item) || (item as number) < 0 || (item as number) > 255)) throw new Error("invalid Wellfriend runtime image bytes"); return { width: numberValue(object.width), height: numberValue(object.height), stride: numberValue(object.stride), pixelFormat: String(object.pixel_format), bytes: Uint8Array.from(bytes as number[]) }; }
function parseQuad(value: unknown, size: { readonly width: number; readonly height: number }, confidence: number, source: "AUTO" | "MANUAL" = "AUTO"): PageGeometry | undefined { const object = parseRecord(value); if (!Array.isArray(object.points) || object.points.length !== 4) return undefined; const points = object.points.map((point) => { const item = parseRecord(point); return { x: numberValue(item.x), y: numberValue(item.y) }; }); const corners: PageGeometry["corners"] = [points[0], points[1], points[2], points[3]]; return { corners, imageSize: size, confidence: Math.max(0, Math.min(1, confidence)), source }; }
function mapAnalysis(value: unknown, frame: PerceptionFrame): FrameAnalysisResult {
  const object = parseRecord(value); const metrics = parseRecord(object.quality); const qualityMetrics: Record<string, { rawValue: number; normalizedScore: number; confidence: number }> = {};
  Object.entries(metrics).forEach(([name, metric]) => { const item = parseRecord(metric); qualityMetrics[name] = { rawValue: numberValue(item.raw_value), normalizedScore: numberValue(item.normalized_score), confidence: numberValue(item.confidence) }; });
  const confidence = numberValue(object.capture_readiness_score); const fused = object.fused_quad ? parseQuad(object.fused_quad, frame.size, confidence) : undefined; const refined = object.refined_quad ? parseQuad(object.refined_quad, frame.size, confidence) : undefined;
  const candidates = Array.isArray(object.candidates) ? object.candidates.map((quad) => { const geometry = parseQuad(quad, frame.size, confidence); if (!geometry) throw new Error("invalid runtime candidate quad"); return { geometry, score: confidence, confidence, source: "native_scalar", diagnostics: [] }; }) : [];
  return { inputSize: frame.size, rotationDegrees: frame.rotationDegrees, mirrored: frame.mirrored, qualityReport: { metrics: qualityMetrics, warnings: [], confidence: .7 }, conditionVector: { conditions: {} }, candidates, fusionResult: { geometry: fused, confidence, contributingSources: ["native_scalar"], rejectedSources: [], disagreementScore: 0 }, refinementResult: { geometry: refined, confidence, diagnostics: stringArray(object.diagnostics) }, temporalState: { stability: 0, stable: false, frameCount: 1 }, captureReadiness: String(object.capture_readiness) as CaptureReadiness, captureReadinessScore: confidence, guidance: stringArray(object.guidance) as CaptureGuidance[], diagnostics: stringArray(object.diagnostics), stageTimingsMillis: {}, engineMode: "WASM" };
}
function filterName(preset: FilterRequest["preset"]): string { return ({ ORIGINAL: "Original", AUTO: "Auto", CLEAN: "Clean", COLOR: "Color", GRAYSCALE: "Grayscale", BLACK_AND_WHITE: "B&W", RECEIPT: "Receipt", BOOK: "Book", WHITEBOARD: "Whiteboard", PHOTO_DOCUMENT: "PhotoDocument" })[preset]; }
