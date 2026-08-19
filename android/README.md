# Wellfriend Scan Android

MP7 turns the Android shell into a modular scanner product. `scanner-core` owns session state, `scanner-perception` owns the only core-binding seam, `scanner-ui` owns Compose and CameraX, and `scanner-export` owns export contracts. No Kotlin detector, homography solver, restoration algorithm, OCR engine, or ML Kit scanner dependency is included.

The native `wellfriend-perception` Android ABI is not yet published. Release builds fail closed through `UnavailableNativePerceptionBridge`; debug builds use `DevMockPerceptionEngine` only to exercise UI/state flow. It is never a production detector.

See [docs/local-build.md](docs/local-build.md) for prerequisites and [docs/architecture.md](docs/architecture.md) for module boundaries.
