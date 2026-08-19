# Wellfriend Scan

MP10 adds real-runtime seams: Android delegates to the Rust C ABI through JNI when a packaged library is present and web delegates to a reviewed WASM module when one is loaded. Missing release runtimes fail closed; test/dev mocks remain visibly non-production.

`wellfriend-scan` is the reference document-scanner product shell for Android, web, and desktop surfaces. It owns product UI, capture lifecycle, user sessions, and export orchestration. It is not a duplicate perception engine: reusable quality, detection, fusion, reconstruction, restoration, and semantic algorithms belong in [`wellfriend-perception`](https://github.com/demisuga01-lab/wellfriend-perception).

`wellfriend-models` supplies validated ONNX artifacts; this repository does not train models or bundle untracked weights.

## Build and test

```powershell
npm ci
npm run build:web
npm run serve:web
npm test
npm run verify:android-contract
npm run verify:web-desktop-contract
npm run verify:dependencies
```

The Android reference app requires a local Android SDK, JDK 17, and Gradle 8.7+ (or a generated wrapper). See [android/README.md](android/README.md).

Current status: MP7 adds the modular Android reference scanner. MP8 adds a browser scanner with safe import, optional webcam, worker transport, canvas crop/overlay, shared sessions, diagnostics, and debug JSON export; it also adds a tested desktop local file/folder workflow contract. Web production perception is a fail-closed WASM seam while the current browser worker mock is test/dev-only. No ML Kit scanner dependency is required. See [android/README.md](android/README.md), [web/README.md](web/README.md), and [desktop/README.md](desktop/README.md).
