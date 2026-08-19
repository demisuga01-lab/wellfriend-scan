# Web scanner architecture

The dependency-free browser reference surface is `app → ScannerShell → WebScanController → WebPerceptionEngine`. Product state comes from `shared/src/scan-session.ts`; it does not implement document detection, reconstruction, or restoration.

`WorkerPerceptionEngine` moves requests to a browser worker. The worker currently uses `DevMockPerceptionEngine` for development and tests only. `WasmPerceptionEngine` fails closed until a verified `wellfriend-perception` WASM artifact is released. Production web use must replace the worker mock with that adapter, not add a TypeScript detector.

Build and run with `npm run build:web` and `npm run serve:web`, then open `http://localhost:4173`.
