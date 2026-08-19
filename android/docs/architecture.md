# Android architecture

`app` wires permissions, activity-result contracts, disk files, and the reference Compose surface. `scanner-core` is pure Kotlin session state and coordinate math. `scanner-perception` contains the MP3/MP4-compatible DTOs and `PerceptionEngine`; `scanner-ui` owns CameraX and rendering; `scanner-export` owns output contracts; `scanner-testing` hosts JVM flow tests.

The allowed flow is CameraX or gallery input -> `PerceptionFrame` -> `PerceptionEngine` -> `ScanController` -> Compose. Product code cannot route around that seam to implement a second detector or reconstruction pipeline.
