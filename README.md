# Wellfriend Scan

MP10B adds reproducible local/CI runtime artifacts: Android delegates to the Rust C ABI through JNI when verified arm64-v8a/x86_64 libraries are synced, and web delegates to a verified local WASM package in its worker. Missing release runtimes fail closed; test/dev mocks remain visibly non-production.

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
npm run verify:runtime-artifacts
```

The Android reference app requires a local Android SDK, JDK 17, and Gradle 8.7+ (or a generated wrapper). See [android/README.md](android/README.md).

Current status: MP7 adds the modular Android reference scanner. MP8 adds a browser scanner with safe import, optional webcam, worker transport, canvas crop/overlay, shared sessions, diagnostics, and debug JSON export; it also adds a tested desktop local file/folder workflow contract. MP10B packages the scalar Rust runtime locally/through CI artifacts, but this is not real-device validation or a production-quality claim. No ML Kit scanner dependency is required. See [android/native artifacts](android/docs/native-artifacts.md), [web/WASM artifacts](web/docs/wasm-artifacts.md), [web/README.md](web/README.md), and [desktop/README.md](desktop/README.md).
