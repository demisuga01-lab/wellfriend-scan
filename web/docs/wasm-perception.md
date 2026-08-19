# WASM perception runtime

`WasmPerceptionEngine` accepts a generated Wellfriend WASM module and a source-image resolver. It sends decoded Gray8/Rgb8/Rgba8 buffers into the Rust scalar runtime, maps returned JSON into existing scanner DTOs, and stores resulting pixels under explicit `wellfriend-runtime://` URIs. It contains no TypeScript detector, warp, or filter.

`WorkerPerceptionEngine.loadWasm()` uses `LOAD_WASM`, `ENGINE_READY`, and `ENGINE_FAILED`. A worker may report ready only for a reviewed `WASM` engine. Missing WASM fails closed; `DevMockPerceptionEngine` is allowed only in tests or explicitly marked dev tooling.

MP10 validates the Rust WASM target but does not publish a web package or assert browser latency.
