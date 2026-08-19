# Runtime artifact contract

`wellfriend-scan` consumes `wellfriend-perception` runtime artifacts through a
single schema-1 contract. Android requires kind `wellfriend-android-abi` with
both C ABI and JNI libraries for arm64-v8a and x86_64. Web requires kind
`wellfriend-wasm-package` with wasm, generated loader, declarations, and an ESM
module marker. Every record has a source SHA and SHA-256 value in both manifest
and checksums file.

Sync scripts validate files before copying them. The web worker validates again
before loading. Release/production failures are explicit; development mocks are
never an automatic replacement. The contract establishes packaging integrity,
not model provenance, OCR/PDF capability, performance, or detection quality.
