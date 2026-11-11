# MP11 Browser WASM Runtime Smoke

The browser smoke proves that a local, manifest-verified package can be loaded by the worker and
calls the real scalar Rust runtime. It is not a real-device latency, OCR, PDF, or quality claim.

## Build and sync

```powershell
cd ..\wellfriend-perception
.\scripts\build-wasm.ps1 -Profile debug
cd ..\wellfriend-scan
.\scripts\sync-wasm.ps1
npm run build:web
npm run serve:web
```

Open `http://localhost:4173/runtime-worker-smoke.html`. A successful result shows a `WASM`
runtime result rather than `ENGINE_FAILED`. The page registers a tiny decoded Gray8 source in the
worker and runs real `analyzeFrame`; it never uses the dev mock.

For the automated host smoke, run:

```powershell
npm test
```

`real-wasm-product-flow.test.mjs` exercises real packaged WASM analysis, manual geometry,
reconstruction, and a scalar filter through the worker-local source-image registry.

## Camera checklist

1. Serve from localhost/HTTPS and grant camera permission.
2. Start the camera, verify video readiness, capture a frame, and confirm the worker reports
   `ENGINE_READY` before analysis.
3. Confirm canvas overlay coordinates still align after viewport resizing and rotation.
4. Deny permission or remove/alter a WASM file. The UI must surface an explicit error; production
   must never substitute `DEV_WEB_MOCK`.

Headless CI validates manifest/checksum, raw-pixel registration, worker protocol, and the actual
WASM scalar flow. It cannot validate a physical camera permission prompt or browser compositor.
