# Manual crop

The editor renders four draggable source-image corner handles. It uses the same coordinate mapper as the live overlay and clamps edits to source bounds. `ScanController.applyManualCrop` validates exactly four finite, in-bounds, non-zero-area, convex corners and labels them `MANUAL` before reconstruction. Invalid geometry never bypasses the binding seam.

MP7 supplies a simple crop view rather than a magnifier. A high-zoom/magnifier experience is deferred.
