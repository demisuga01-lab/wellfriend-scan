# MP10 feature matrix

| Capability | Android | Web | Desktop | Status |
| --- | --- | --- | --- | --- |
| Camera/upload/session/crop UX | reference app | reference app | workflow contract | implemented surface |
| Scalar document analysis | packaged C-ABI/JNI artifact | packaged local WASM worker artifact | web/shared contract | real scalar engine where a verified artifact is synced |
| Native/WASM missing runtime | release error | worker `ENGINE_FAILED` | n/a | fail-closed, never mock fallback |
| Dev perception mock | debug/test only | test/direct-handler only | test only | mock/dev-only |
| Quad boundary detection | scalar baseline | scalar baseline | future | experimental |
| Shape-general boundary contract | shared runtime JSON | shared runtime JSON | shared contract | implemented contract; algorithms future |
| Perspective reconstruction | scalar C ABI path | scalar WASM path | future | experimental |
| Original/Auto/Clean/Color/Grayscale/B&W | scalar C ABI path | scalar WASM path | future | experimental |
| OCR/PDF/model inference | no | no | no | not implemented |

No MP10 claim compares Wellfriend with Google ML Kit, Google Drive, Adobe Scan, or MakeACopy. That requires real-device datasets, reviewed production artifacts, and published benchmark evidence.
