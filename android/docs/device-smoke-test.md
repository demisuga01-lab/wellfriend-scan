# MP11 Android Runtime Smoke

This smoke validates the packaged scalar runtime; it does not claim scanner-quality parity,
OCR, PDF output, trained-model behavior, or perfect boundaries.

## Preconditions

Use JDK 17, Android SDK 35, an Android NDK for artifact creation, and a real device or emulator
whose ABI is `arm64-v8a` or `x86_64`. Build artifacts from the reviewed perception commit, then
sync them before Android assembly:

```powershell
cd ..\wellfriend-perception
$env:ANDROID_NDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.1.12297006"
.\scripts\build-android-abi.ps1 -Profile debug
cd ..\wellfriend-scan
.\scripts\sync-android-abi.ps1
cd android
.\gradlew.bat :app:assembleDebug
```

The artifact manifest and checksums are copied to the APK assets. The loader expects both
`libwellfriend_perception.so` and `libwellfriend_perception_jni.so` for the selected ABI.

## Device checklist

1. Install the debug APK and grant camera permission.
2. Open the debug panel. It must show `NATIVE`, a loaded JNI runtime, the artifact source SHA,
   schema `1`, frame dimensions, rotation, and mirror state. A `DEV_JVM_MOCK` label is not a real
   runtime result.
3. Point the back camera at a high-contrast page. Confirm that the overlay changes with the
   native guidance/geometry returned by Rust.
4. Rotate the device through all four orientations. The overlay corners must stay on the same
   physical page edges; test front-camera mirroring separately if that camera is enabled.
5. Import a guarded gallery image, edit a manual crop, reconstruct, and apply `Grayscale`.
   Gallery pixels are explicitly registered with the native bridge; no URI/path decoder fallback
   is permitted.
6. Disconnect or remove the JNI artifact and repeat a release build. It must surface
   `NativeRuntimeUnavailable`; it must not select the dev mock.

Run instrumentation tests when a device/emulator is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Current boundary

CameraX analysis sends a compact, crop-aware Gray8 Y plane to Rust. An ImageCapture file is
explicitly decoded through the same 25 MB / 20 MP guardrails, sampled to the scalar runtime's
bounded RGBA cache, and registered before native reconstruction/filtering. The product never
silently decodes arbitrary paths or silently falls back to a mock.
