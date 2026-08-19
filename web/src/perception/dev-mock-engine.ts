import type { PageGeometry } from "../../../shared/src/scan-session.js";
import type { FilterRequest, FilterResult, FrameAnalysisResult, PerceptionFrame, ReconstructionRequest, ReconstructionResult, WebPerceptionEngine } from "./contracts.js";

/** Test/dev-only flow double. It is intentionally not a document detector or restorer. */
export class DevMockPerceptionEngine implements WebPerceptionEngine {
  readonly mode = "DEV_WEB_MOCK" as const;
  async analyzeFrame(frame: PerceptionFrame): Promise<FrameAnalysisResult> {
    const geometry: PageGeometry = { corners: [{ x: frame.size.width * .1, y: frame.size.height * .1 }, { x: frame.size.width * .9, y: frame.size.height * .1 }, { x: frame.size.width * .9, y: frame.size.height * .9 }, { x: frame.size.width * .1, y: frame.size.height * .9 }], imageSize: frame.size, confidence: .9, source: "AUTO" };
    return { inputSize: frame.size, rotationDegrees: frame.rotationDegrees, mirrored: frame.mirrored, qualityReport: { metrics: { mock_quality: { rawValue: 1, normalizedScore: 1, confidence: 0 } }, warnings: [], confidence: 0 }, conditionVector: { conditions: {} }, candidates: [{ geometry, score: .9, confidence: .9, source: "dev_web_mock", diagnostics: [] }], fusionResult: { geometry, confidence: .9, contributingSources: ["dev_web_mock"], rejectedSources: [], disagreementScore: 0 }, refinementResult: { geometry, confidence: .9, diagnostics: ["dev_only_mock"] }, temporalState: { stability: .95, stable: true, frameCount: 1 }, captureReadiness: "CAPTURE_NOW", captureReadinessScore: .95, guidance: ["READY"], diagnostics: ["dev_only_mock_perception; not production detection"], stageTimingsMillis: { mock: 0 }, engineMode: this.mode };
  }
  async reconstructPage(request: ReconstructionRequest): Promise<ReconstructionResult> { return { outputUri: request.sourceUri, outputSize: request.sourceSize, diagnostics: ["dev_only_mock_reconstruction; no pixel transform was performed"], confidence: request.geometry.confidence, engineMode: this.mode }; }
  async applyFilter(request: FilterRequest): Promise<FilterResult> { return { outputUri: request.inputUri, appliedProcessorIds: [], diagnostics: ["dev_only_mock_filter; no pixels changed"], engineMode: this.mode }; }
}
