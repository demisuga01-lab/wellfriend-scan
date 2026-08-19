# Android instrumentation seam

MP7 keeps deterministic scanner state-machine and integration coverage on the JVM so
it can run without an emulator, camera, or native perception ABI. A future connected
device suite belongs here and must exercise permission handling, CameraX lifecycle
binding, preview/overlay alignment, gallery import, and Compose navigation against
`NativePerceptionEngine` once the Android ABI is published.

Run it locally when device tests are added with:

```powershell
& .\gradlew.bat :app:connectedDebugAndroidTest
```
