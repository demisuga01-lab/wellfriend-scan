# Coordinate mapping

`CanvasCoordinateMapper` maps source image pixels to device-pixel canvas coordinates and back. It handles fit/fill, CSS canvas dimensions, device pixel ratio, rotations of 0/90/180/270 degrees, letterboxing/cropping, and mirroring. Manual crop coordinates are converted back to source-image pixels before `PageGeometry` validation and reconstruction.
