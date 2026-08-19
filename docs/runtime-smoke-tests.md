# Runtime smoke tests

Run the real scalar-runtime packaging smoke after building and syncing artifacts:

```powershell
cd ..\wellfriend-perception
$env:ANDROID_NDK_ROOT = "C:\\Android\\Sdk\\ndk\\27.1.12297006"
.\scripts\build-android-abi.ps1 -Profile release
.\scripts\build-wasm.ps1 -Profile release
cd ..\wellfriend-scan
.\scripts\sync-android-abi.ps1
.\scripts\sync-wasm.ps1
npm run verify:runtime-artifacts
npm test
```

The Web Node smoke initializes the real WASM package and calls the Rust scalar
analysis path. Android host coverage validates manifest/library names, loader
selection, JSON mapping, and release fail-closed behavior. Android assembly or a
physical-device smoke requires a Gradle/SDK environment and is not implied by
these host checks.
