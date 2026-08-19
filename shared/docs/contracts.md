# Cross-platform scanner contracts

TypeScript contract values mirror Android MP7 values exactly: `CaptureMode`, `CaptureGuidance`, `FilterPreset`, page/state/status names, `PageGeometry`, `ScanSession`, `ReconstructionRequest`, `FilterRequest`, and export formats. Geometry is TL/TR/BR/BL in source-image pixels; confidence is finite and in `[0,1]`; manual geometry is `MANUAL` and validated before it reaches reconstruction.

| Kotlin MP7 | TypeScript MP8 | Future Rust/WASM |
| --- | --- | --- |
| `PerceptionEngine` | `WebPerceptionEngine` | `DomainPack` binding adapter |
| `FrameAnalysisResult` | `FrameAnalysisResult` | serialized MP3 evidence graph |
| `ReconstructionRequest` | `ReconstructionRequest` | MP4 planar/surface request |
| `FilterRequest` | `FilterRequest` | MP4 processing-plan request |

No model artifact is loaded directly by a product surface. Future WASM/native adapters validate the MP5/MP6 artifact contract before inference.
