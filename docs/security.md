# Security and input handling

Android gallery and web imports bound file size/dimensions and reject invalid data before session use. Web limits MIME types and cleans object URLs. Debug export records metadata/URI references only, never image bytes. Desktop workflow accepts only host-approved descriptors and does not recursively read arbitrary paths.

Future native/WASM adapters must validate artifact hashes, handle untrusted worker messages, bound allocation/decode sizes, and preserve fail-closed behavior. Report vulnerabilities via `SECURITY.md`.
