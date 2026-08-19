# Perception binding

`PerceptionEngine` is the Android contract for `analyzeFrame`, `reconstructPage`, and `applyFilter`. Its DTOs preserve MP3/MP4 names and evidence: `QualityReport`, candidates, `FusionResult`, `RefinementResult`, `TemporalState`, `CaptureReadiness`, canonical reconstruction, conditions, and processing diagnostics.

MP10 adds a Rust C ABI, JNI source shim, `JniNativePerceptionBridge`, and strict JSON mapper. The native library itself is not yet cross-compiled/packaged for Android ABIs, so release still fails closed when it cannot load. `DevMockPerceptionEngine` is explicitly test/debug only; when JNI loads it is preferred even in debug. The scanner controller and UI remain unchanged.
