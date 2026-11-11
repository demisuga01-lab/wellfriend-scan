import type { FilterRequest, FilterResult, FrameAnalysisResult, PerceptionFrame, ReconstructionRequest, ReconstructionResult } from "../perception/contracts.js";
import type { RuntimeImage } from "../perception/worker-engine.js";
export type WorkerRequest = { readonly requestId: string; readonly type: "ANALYZE_FRAME"; readonly frame: PerceptionFrame }
  | { readonly requestId: string; readonly type: "RECONSTRUCT_PAGE"; readonly request: ReconstructionRequest }
  | { readonly requestId: string; readonly type: "APPLY_FILTER"; readonly request: FilterRequest }
  | { readonly requestId: string; readonly type: "REGISTER_IMAGE"; readonly uri: string; readonly image: RuntimeImage }
  | { readonly requestId: string; readonly type: "CANCEL_JOB" | "PING" | "LOAD_WASM" };
export type WorkerResponse = { readonly requestId: string; readonly type: "ANALYSIS_RESULT"; readonly result: FrameAnalysisResult }
  | { readonly requestId: string; readonly type: "RECONSTRUCTION_RESULT"; readonly result: ReconstructionResult }
  | { readonly requestId: string; readonly type: "FILTER_RESULT"; readonly result: FilterResult }
  | { readonly requestId: string; readonly type: "PONG" }
  | { readonly requestId: string; readonly type: "IMAGE_REGISTERED" }
  | { readonly requestId: string; readonly type: "ENGINE_READY" }
  | { readonly requestId: string; readonly type: "ENGINE_FAILED"; readonly message: string }
  | { readonly requestId: string; readonly type: "ERROR"; readonly message: string };
