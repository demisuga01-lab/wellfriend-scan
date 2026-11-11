# Wellfriend Scan

MP11 validates the real scalar runtime route: Android CameraX sends compact crop-aware Gray8 frames to the Rust C ABI through JNI when verified arm64-v8a/x86_64 libraries are synced; web decodes uploads/camera frames to raw RGBA and calls a verified local WASM package in its worker. Missing production runtimes fail closed; test/dev mocks remain visibly non-production.

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

The Android reference app requires a local Android SDK and JDK 17. The committed Gradle 8.7 wrapper verifies the official distribution checksum. See [Android build instructions](android/docs/building.md) and the [device smoke checklist](android/docs/device-smoke-test.md).

Current status: MP7 adds the modular Android reference scanner. MP8 adds a browser scanner with safe import, optional webcam, worker transport, canvas crop/overlay, shared sessions, diagnostics, and debug JSON export; it also adds a tested desktop local file/folder workflow contract. MP11 adds host-validated real scalar WASM analysis/reconstruction/filtering and Android CameraX crop/stride calibration coverage. Physical-device and physical-browser-camera validation remain a documented manual gate, not a production-quality claim. No ML Kit scanner dependency is required. See [android/native artifacts](android/docs/native-artifacts.md), [web/WASM artifacts](web/docs/wasm-artifacts.md), [browser smoke](web/docs/real-wasm-browser-smoke.md), and [desktop/README.md](desktop/README.md).
