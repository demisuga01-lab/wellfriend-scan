# Browser WASM runtime artifact

Create the package in `wellfriend-perception`, then sync it locally:

```powershell
cd ..\wellfriend-perception
.\scripts\build-wasm.ps1 -Profile release
cd ..\wellfriend-scan
.\scripts\sync-wasm.ps1
npm run verify:runtime-artifacts
npm test
```

The sync script accepts only the schema-1 local `wellfriend-wasm-package` with a
source SHA, expected wasm-bindgen exports, required `.wasm`/loader/declaration/
module-marker files, and matching SHA-256 entries. It writes generated output to
`web/public/wasm`; those files are ignored by Git.

The production worker verifies manifest and file hashes before importing the local
ESM loader, initializes the verified WASM bytes, and fails with `ENGINE_FAILED`
when absent or invalid. It never replaces a missing production runtime with a mock.
The Node smoke executes the synced package on a deterministic scalar fixture;
browser-camera and real-device measurements remain untested.
