# Scan session

`ScanSession` stores page URI references, optional thumbnails, detected/manual geometry, canonical preview reference, filter choice, page state, processing status, and diagnostics. It does not hold full images. The pure `ScanSessionReducer` supports add, delete, reorder, rotate, and update actions, making multi-page review host-testable.

The controller state machine covers permission, camera startup, search, candidate, almost-ready, ready, capture, review, editing, filtering, acceptance, export, and error states. Auto capture is permitted only for core `CaptureNow`; manual capture is an explicit user action.
