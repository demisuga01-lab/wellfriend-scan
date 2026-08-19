# MP11 runtime validation feature matrix

| Capability | Android | Web | Desktop | Status |
| --- | --- | --- | --- | --- |
| Camera/upload/session/crop UX | reference app | reference app | workflow contract | implemented surface |
| Scalar document analysis | CameraX crop-aware Gray8 to packaged C-ABI/JNI artifact | decoded upload/camera pixels to packaged local WASM worker | web/shared contract | real scalar engine where a verified artifact is synced |
| Native/WASM missing runtime | release error | worker `ENGINE_FAILED` | n/a | fail-closed, never mock fallback |
| Dev perception mock | debug/test only | test/direct-handler only | test only | mock/dev-only |
| Quad boundary detection | scalar baseline | scalar baseline | future | experimental |
| Shape-general boundary contract | shared runtime JSON | shared runtime JSON | shared contract | implemented contract; algorithms future |
| Perspective reconstruction | explicit native image registry for gallery pixels | worker-local image registry | future | experimental scalar runtime |
| Original/Auto/Clean/Color/Grayscale/B&W | scalar C ABI path with registered pixels | scalar WASM path with registered pixels | future | experimental scalar runtime |
| OCR/PDF/model inference | no | no | no | not implemented |

MP11 verifies packaging and host-level flows. It does not claim Google ML Kit parity, real-device
performance, perfect edge detection, production scanner quality, OCR, PDF, or trained-model
quality. Those require real-device datasets and published benchmark evidence.
