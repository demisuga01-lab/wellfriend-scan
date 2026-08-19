# Perception binding

`PerceptionEngine` is the Android contract for `analyzeFrame`, `reconstructPage`, and `applyFilter`. Its DTOs preserve MP3/MP4 names and evidence: `QualityReport`, candidates, `FusionResult`, `RefinementResult`, `TemporalState`, `CaptureReadiness`, canonical reconstruction, conditions, and processing diagnostics.

There is no published Rust Android C ABI/JNI library in `wellfriend-perception` yet. MP7 therefore implements the generated-binding seam (`NativePerceptionBridge`) and an unavailable release bridge. `DevMockPerceptionEngine` is explicitly test/debug only. When the ABI arrives, it must implement the bridge; the scanner controller and UI stay unchanged.
