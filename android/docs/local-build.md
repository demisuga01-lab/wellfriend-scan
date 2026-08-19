# Local build

Prerequisites: JDK 17, Gradle 8.7, Android SDK platform 35, Android build-tools 34.0.0, and an `ANDROID_HOME` or `ANDROID_SDK_ROOT` value. AGP 8.6.1 targets API 35 and requires Gradle 8.7/JDK 17.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\Android\Sdk"
gradle -p android :scanner-core:test :scanner-testing:test :app:assembleDebug
```

Instrumentation/Compose smoke tests require an emulator or attached device and are intentionally deferred. MP7 verified `:app:assembleDebug` with JDK 17, Android SDK Platform 35, and a cached Gradle 8.11.1 distribution after explicitly setting `JAVA_HOME` and `ANDROID_HOME`; no emulator/device test result is claimed. A Gradle wrapper is still not committed, so contributors need an installed Gradle command until a verified wrapper is added.

MP10 adds the JNI source seam, but this repository does not package its native `.so` libraries. Build the Rust C ABI for each intended Android target, compile `scanner-perception/src/main/jni/wellfriend_perception_jni.c` with the NDK against `wellfriend_perception.h`, then package the resulting `wellfriend_perception_jni` and C ABI libraries under the matching `jniLibs/<abi>` directory. Until that reproducible packaging step exists, release intentionally fails closed.
