# Android scanner feature matrix

Wellfriend targets ML Kit-level scanner UX and aims to surpass it through customization and
transparency, but parity or superiority requires real-device benchmark evidence.

| Capability | Status | Evidence |
| --- | --- | --- |
| Auto document detection | Experimental scalar baseline | Rust native runtime contract and synthetic tests |
| Auto capture | Experimental | Native readiness input and explicit UI mode; physical calibration pending |
| Manual capture | Implemented | CameraX `ImageCapture` path |
| Crop / manual crop | Implemented | Four-corner validated editor |
| Rotate | Implemented | Per-page preview/share rotation |
| Multi-page | Implemented | Session strip, selection, delete, reorder, retake |
| Gallery import | Implemented | Guarded decoder and native pixel registration |
| Original / Auto / Clean / Color / Grayscale / B&W | Scalar baseline | Native filter request and rendered output store |
| Advanced filters | Coming soon | No fake fallback for Receipt/Book/Whiteboard/Photo Document |
| JPEG / PNG share | Implemented | Platform encoder + FileProvider for registered runtime pixels |
| PDF export | Coming soon | Explicit platform placeholder |
| Torch / switch camera | Implemented | CameraX controls |
| Quality guidance / overlay | Experimental scalar baseline | Rust analyze response |
| Runtime diagnostics | Implemented | Separate route and stable log tags |
| Custom theme / callbacks / features | Implemented | `WellfriendScannerOptions` |
| Runtime pluggability | Implemented | Native bridge + explicit dev mock boundary |
| Confidence / uncertainty / manual correction | Experimental scalar baseline | Runtime DTOs and crop correction path |

No row above is a Google ML Kit comparison claim. Physical-device acceptance remains mandatory for
camera stability, scanner quality, and product-flow claims.
