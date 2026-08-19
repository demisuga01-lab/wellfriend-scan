# Desktop architecture

MP8 selects a lightweight host-shell contract rather than shipping Electron, Tauri, or another desktop runtime. `DesktopScannerWorkflow` accepts host-approved file/folder metadata and produces the same platform-neutral `ScanSession` and debug export shape as web/Android. A future Tauri, desktop PWA, or native shell must use this workflow and `WebPerceptionEngine`; it must not fork detection or reconstruction.
