import type { CaptureGuidance, CaptureReadiness, FilterPreset, ImageSize, PageGeometry } from "../../../shared/src/scan-session.js";

export type PerceptionEngineMode = "WASM" | "WORKER" | "DEV_WEB_MOCK";
export interface PerceptionFrame { readonly frameId: string; readonly timestampMillis: number; readonly size: ImageSize; readonly rotationDegrees: 0 | 90 | 180 | 270; readonly mimeType: string; readonly bytes: ArrayBuffer; readonly source: "upload" | "camera" | "paste"; readonly mirrored: boolean; }
export interface QualityMetric { readonly rawValue: number; readonly normalizedScore: number; readonly confidence: number; }
export interface QualityReport { readonly metrics: Readonly<Record<string, QualityMetric>>; readonly warnings: readonly string[]; readonly confidence: number; }
export interface ConditionEvidence { readonly score: number; readonly confidence: number; readonly source: string; readonly recommendedProcessorIds: readonly string[]; }
export interface ConditionVector { readonly conditions: Readonly<Record<string, ConditionEvidence>>; }
export interface DetectionCandidate { readonly geometry: PageGeometry; readonly score: number; readonly confidence: number; readonly source: string; readonly diagnostics: readonly string[]; }
export interface FusionResult { readonly geometry?: PageGeometry; readonly confidence: number; readonly contributingSources: readonly string[]; readonly rejectedSources: readonly string[]; readonly disagreementScore: number; }
export interface RefinementResult { readonly geometry?: PageGeometry; readonly confidence: number; readonly diagnostics: readonly string[]; }
export interface TemporalState { readonly stability: number; readonly stable: boolean; readonly frameCount: number; }
export interface FrameAnalysisResult {
  readonly inputSize: ImageSize; readonly rotationDegrees: number; readonly mirrored: boolean; readonly qualityReport: QualityReport; readonly conditionVector: ConditionVector;
  readonly candidates: readonly DetectionCandidate[]; readonly fusionResult: FusionResult; readonly refinementResult: RefinementResult; readonly temporalState: TemporalState;
  readonly captureReadiness: CaptureReadiness; readonly captureReadinessScore: number; readonly guidance: readonly CaptureGuidance[]; readonly diagnostics: readonly string[]; readonly stageTimingsMillis: Readonly<Record<string, number>>; readonly engineMode: PerceptionEngineMode;
}
export interface ReconstructionRequest { readonly pageId: string; readonly sourceUri: string; readonly sourceSize: ImageSize; readonly geometry: PageGeometry; readonly outputLongEdge: number; readonly aspectPolicy: "free_from_quad"; readonly orientationPolicy: "preserve_source"; readonly cropMarginPolicy: "safe_inner"; }
export interface ReconstructionResult { readonly outputUri: string; readonly outputSize: ImageSize; readonly diagnostics: readonly string[]; readonly confidence: number; readonly engineMode: PerceptionEngineMode; }
export interface FilterRequest { readonly pageId: string; readonly inputUri: string; readonly preset: FilterPreset; readonly conditionVector: ConditionVector; }
export interface FilterResult { readonly outputUri: string; readonly appliedProcessorIds: readonly string[]; readonly diagnostics: readonly string[]; readonly engineMode: PerceptionEngineMode; }

/** The only web-to-perception boundary; UI/session code cannot implement algorithms. */
export interface WebPerceptionEngine { readonly mode: PerceptionEngineMode; analyzeFrame(frame: PerceptionFrame): Promise<FrameAnalysisResult>; reconstructPage(request: ReconstructionRequest): Promise<ReconstructionResult>; applyFilter(request: FilterRequest): Promise<FilterResult>; dispose?(): void; }
