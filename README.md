# Wellfriend Scan

`wellfriend-scan` is the reference document-scanner product shell for Android, web, and later desktop surfaces. It owns product UI, capture lifecycle, user sessions, and export orchestration. It is not a duplicate perception engine: reusable quality, detection, fusion, reconstruction, restoration, and semantic algorithms belong in [`wellfriend-perception`](https://github.com/wellfriend/wellfriend-perception).

`wellfriend-models` supplies validated ONNX artifacts; this repository does not train models or bundle untracked weights.

## Build and test

```powershell
npm install
npm run build:web
npm test
npm run verify:android-contract
```

The MP1 Android Gradle project is intentionally a source/build-boundary skeleton. It requires a local Android SDK plus Gradle 8.6.1 (or a generated wrapper) and JDK 17 to run `./gradlew :app:assembleDebug`; this bootstrap environment has neither the Android SDK nor Gradle, so an Android binary is not claimed as built. See [android/README.md](android/README.md).

Current status: shared scanner state and the TypeScript worker contract are tested; Android has buildable source/module boundaries pending SDK-backed validation. License: Apache-2.0; planned dependencies are tracked in `third_party/dependency-register.toml`.

