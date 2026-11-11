# Android 16 KiB native page compatibility

`dev.wellfriend.scan` accepts Android native runtime artifacts only when their manifest declares
`page_size_alignment_bytes: 16384`. The scanner sync scripts also validate checksums before copying
the Rust and JNI shared libraries into `app/src/main/jniLibs`.

Build and validate in `wellfriend-perception` before syncing:

```powershell
$env:ANDROID_NDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.1.12297006"
Set-Location ..\..\wellfriend-perception
.\scripts\build-android-abi.ps1 -Profile release
.\scripts\check-android-page-size.ps1 -ArtifactRoot .\target\wellfriend-android

Set-Location ..\wellfriend-scan
.\scripts\sync-android-abi.ps1 -SourceRoot ..\wellfriend-perception\target\wellfriend-android
```

The perception validator inspects the ELF `PT_LOAD` alignment of both
`libwellfriend_perception.so` and `libwellfriend_perception_jni.so`. This removes native
page-size compatibility warnings when the artifact is correctly linked; it does not prove
CameraX behavior or physical-device camera quality.
