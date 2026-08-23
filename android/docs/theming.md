# Theming and layout

The scanner camera surface is edge-to-edge but its controls are placed inside Compose padding so
the PreviewView remains unobstructed and the primary capture control stays clear of Samsung
cutouts and navigation areas. The reference layout uses:

- full-screen `PreviewView` with `COMPATIBLE` / `FILL_CENTER`;
- a compact top brand/runtime row;
- a centered guidance card;
- one rounded bottom control surface with a large, centred capture target;
- a separate diagnostics screen rather than a permanent debug wall.

`ScannerTheme` lets an SDK host select the brand name, accent color, theme mode, and preferred
control placement. Host apps should test their choices on physical portrait and landscape devices;
theme configuration does not change native coordinate conventions or camera frame handling.
