# WASM perception runtime

`WasmPerceptionEngine` accepts a generated Wellfriend WASM module and a source-image resolver. It sends decoded Gray8/Rgb8/Rgba8 buffers into the Rust scalar runtime, maps returned JSON into existing scanner DTOs, and stores resulting pixels under explicit `wellfriend-runtime://` URIs. It contains no TypeScript detector, warp, or filter.

`WorkerPerceptionEngine.loadWasm()` uses `LOAD_WASM`, `ENGINE_READY`, and `ENGINE_FAILED`. A worker may report ready only for a reviewed `WASM` engine. Missing WASM fails closed; `DevMockPerceptionEngine` is allowed only in tests or explicitly marked dev tooling.

MP10B builds a local/CI browser package with source-SHA manifest and checksums, then the worker validates and loads it from `/wasm`. It does not assert browser latency, camera behavior, or scanner quality.
