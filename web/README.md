# Wellfriend Scan Web

MP8 provides a dependency-free browser scanner reference surface: safe file import/drag-and-drop, optional webcam capture, canvas overlay/crop editing, page review, filter/reconstruction requests, diagnostics, worker messaging, and debug JSON export.

Run `npm run build:web` followed by `npm run serve:web`. The current worker uses an explicit dev-only mock. Production perception requires a reviewed `wellfriend-perception` WASM artifact; it must not be replaced by a TypeScript detector.
