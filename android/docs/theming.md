# Theming and layout

The scanner camera surface is edge-to-edge but its controls are placed inside Compose padding so
the PreviewView remains unobstructed and the primary capture control stays clear of Samsung
cutouts and navigation areas. The reference layout uses:

- full-screen `PreviewView` with `COMPATIBLE` / `FILL_CENTER`;
- a compact top brand/runtime row;
- one concise guidance pill;
- one rounded bottom control surface with a single centred capture target and compact secondary actions;
- a separate diagnostics screen rather than a permanent debug wall.

`ScannerTheme` lets an SDK host select the brand name, accent color, theme mode, and preferred
control placement. The reference settings screen lets a user exercise accent and control placement
choices without introducing a second scanner implementation. Host apps should test their choices on
physical portrait and landscape devices; theme configuration does not change native coordinate
conventions or camera frame handling.
