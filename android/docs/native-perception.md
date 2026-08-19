# Native perception runtime

`JniNativePerceptionBridge` is the Android implementation of `NativePerceptionBridge`. It calls only the exported `wf_*` functions through the small C source shim at `scanner-perception/src/main/jni/wellfriend_perception_jni.c`; Kotlin does not contain detection, homography, or restoration code.

`PerceptionEngineFactory` prefers JNI when `wellfriend_perception_jni` loads. Debug may choose `DevMockPerceptionEngine` when absent and exposes `DEV_JVM_MOCK`; release uses `UnavailableNativePerceptionBridge` and fails closed. MP10B supplies reproducible arm64-v8a/x86_64 build and verified sync scripts; the generated artifacts are local/CI artifacts, not committed opaque binaries. A physical-device smoke remains outstanding.

Frame bytes must be converted to Gray8/Rgb8/Bgr8/Rgba8 before native analysis. Multi-plane CameraX YUV is rejected by this first scalar ABI rather than being guessed or misread.
