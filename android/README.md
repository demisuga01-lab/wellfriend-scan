# Android scanner skeleton

The Android app owns Kotlin/Compose UI, CameraX/Camera2 capture, permissions, orientation, autofocus/exposure/torch/zoom controls, IMU hooks, overlays, and user flow. The planned `wellfriend-perception` Android binding owns reusable image analysis and must be called through a thin JNI/FFI boundary rather than copied into the app.

MP1 defines the module and Compose screen only. To build locally, install Android SDK platform 35, Android build tools, JDK 17, and Gradle 8.6.1, then run `gradle :app:assembleDebug` from this directory (or generate and commit a verified wrapper in a future setup milestone). This environment has no Android SDK or Gradle and does not claim an Android binary build. Planned product work includes live CameraX analysis, high-resolution capture, focus/exposure, torch, zoom, orientation, IMU, auto/manual capture, gallery import, crop, multi-page scanning, filters, OCR, and PDF export.

