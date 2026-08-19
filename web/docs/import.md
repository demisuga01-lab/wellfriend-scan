# Browser import

The import panel accepts dropped or picked JPEG, PNG, and WebP images. It rejects unknown MIME types, files larger than 25 MiB, decode failures, and images above 20 megapixels. Decode uses `createImageBitmap`; source blobs stay outside session metadata and object URLs are tracked and revoked when pages are deleted or the shell closes.

Clipboard and EXIF normalization are deferred. Import remains available even when camera permission is denied.
