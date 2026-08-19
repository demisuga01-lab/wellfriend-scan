# Coordinate mapping

Perception geometry is always source-image pixels. `PreviewCoordinateMapper` transforms between those pixels and the CameraX preview using 0/90/180/270 rotation, fit/fill scaling, letterbox/crop offsets, and mirroring. The inverse is tested for each rotation.

Analysis geometry is scaled into the high-resolution capture coordinate space before it enters a `ReconstructionRequest`. This assumes the capture and analysis streams share the same crop/aspect; a future native capture adapter must supply an explicit crop transform for cameras where that assumption is false.
