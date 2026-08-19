# Web scan session

The session supports add, select, delete, reorder, rotate, manual crop, reconstruction, filters, and review metadata. Source blobs and thumbnails are held by the UI asset store; `ScanSession` retains only URI/size/diagnostic references. This mirrors Android MP7’s reducer and DTO naming.
