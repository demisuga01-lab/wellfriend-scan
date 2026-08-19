# Runtime contracts

The Rust C ABI/WASM schema uses `AnalyzeFrameRequest/Response`, `ReconstructPageRequest/Response`, `ApplyFilterRequest/Response`, and structured runtime errors at schema version `1`. It uses top-left source-pixel coordinates, x-right/y-down, TL/TR/BR/BL quad order, finite `[0,1]` confidence, and explicit boundary evidence/limitations.

Kotlin and TypeScript preserve the MP7 names (`FrameAnalysisResult`, `FusionResult`, `RefinementResult`, `CaptureReadiness`, `ReconstructionResult`, and `FilterResult`). Platform adapters may map omitted scalar fields to explicit diagnostics but cannot silently replace the Rust runtime with a product detector.
