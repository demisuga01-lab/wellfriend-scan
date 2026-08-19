# Roadmap

## MP7 — Android reference scanner

Implemented: modular Kotlin/Compose app boundaries, CameraX preview/analysis/capture ownership, permission/gallery contracts, `PerceptionEngine` binding seam, controller/session state machine, coordinate mapping, crop flow, filters/reconstruction requests, diagnostics, debug JSON export, and host test structure.

Deferred: the published wellfriend-perception Android ABI, production model inference, OCR, PDF generation, real-device instrumentation, and full camera crop-transform calibration.

## MP8 — Web and desktop scanner surfaces

Implemented: platform-neutral TypeScript DTOs matching Android MP7, browser file/drag-drop import guardrails, optional webcam capture, worker perception transport, dev-only mock and fail-closed WASM seams, canvas coordinate mapping/manual crop, multi-page controller, filters/reconstruction requests, diagnostics, JSON debug export, and a desktop host file/folder workflow contract.

Deferred: reviewed production WASM/native perception artifacts, clipboard/EXIF handling, desktop packaging, browser camera device tests, pixel/image export, OCR, and PDF generation. MP9 will harden end-to-end interoperability and release gates.
