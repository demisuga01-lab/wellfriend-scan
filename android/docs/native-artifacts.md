# Native runtime artifacts

Build `wellfriend-perception` first, then sync only its verified artifact:

```powershell
cd ..\wellfriend-perception
$env:ANDROID_NDK_ROOT = "C:\\Android\\Sdk\\ndk\\27.1.12297006"
.\scripts\build-android-abi.ps1 -Profile release
cd ..\wellfriend-scan
.\scripts\sync-android-abi.ps1
npm run verify:runtime-artifacts
```

The sync script requires a `wellfriend-android-abi` schema-1 manifest, a source
SHA, both arm64-v8a/x86_64 C ABI and JNI libraries, and matching SHA-256 values.
It copies native libraries into `app/src/main/jniLibs` and signed metadata into
app assets; generated libraries and manifests are ignored by Git. Gradle packages
that location into the app. `NativeRuntimeArtifactContract` provides host-testable
metadata validation, while sync verifies real library bytes before packaging.

Release never falls back to the development mock. If the libraries are absent or
cannot load, it reports `NativeRuntimeUnavailable`. A successful package smoke is
not a real-device performance, camera, or scanner-quality claim.
