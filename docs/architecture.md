# Product architecture

`shared` owns platform-neutral product-state concepts. Android, web, and future desktop hosts own capture/UI lifecycle and pass observations to a `wellfriend-perception` binding. The perception engine returns reconstruction and semantic outputs; the product owns review, manual correction, multi-page session state, and export choices. `wellfriend-models` is the only source of validated model artifacts.

MP8 keeps browser work off the UI thread through `WorkerPerceptionEngine`, with an explicit future `WasmPerceptionEngine`. Desktop hosts supply locally approved file/folder metadata through `DesktopScannerWorkflow`. Neither path implements a detector, warp, or restoration filter in product code.
