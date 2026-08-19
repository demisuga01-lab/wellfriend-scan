# Export

`ExportRequest`, `ExportFormat`, `ExportResult`, `ExportProgress`, and `ExportError` establish product plumbing. MP7 writes a JSON debug session export that intentionally contains URI references and metadata only. JPEG, PNG, and `PDF_PLACEHOLDER` return an explicit unimplemented result; no PDF library is added.
