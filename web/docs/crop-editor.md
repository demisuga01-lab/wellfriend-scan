# Crop editor

The static overlay draws detected/refined geometry and exposes four draggable handles. Applying a crop makes a `MANUAL` `PageGeometry`; reset returns to detected geometry. Geometry requires four finite, in-bounds, convex, non-zero-area corners. The editor never performs a local perspective warp; it calls `reconstructPage` through the perception boundary.
