# Product-flow proof

## MP11 real-runtime extension

MP11 additionally proves the packaged Rust WASM scalar route in Node: registered decoded pixels,
real analysis, manual geometry, real reconstruction, and a real scalar filter. Android source is
compiled against the packaged JNI artifacts and the debug APK contains both required ABIs and the
artifact manifest. Android host/product-flow tests still use explicit mocks where the Android JNI
library cannot execute on a desktop JVM.

This does not establish physical camera hardware behavior, real-device performance, Google ML Kit
parity, production scanner quality, OCR, or PDF output.

Android host, web integration, and desktop workflow tests prove: source frame/import → dev/mock analysis → overlay/manual geometry → multi-page review → reconstruction request → filter request → debug JSON export. The tests intentionally use mocks and assert their diagnostics. They do not prove production detection, reconstruction pixels, camera hardware, OCR, or PDF output.
