# Scanner options reference

| Option | Effect |
| --- | --- |
| `theme` | Brand name, system/light/dark choice, accent, and control placement preference. |
| `mode` | Product intent (`DOCUMENT`, `RECEIPT`, `ID_CARD`, `WHITEBOARD`); it does not claim a new detector. |
| `enabledFeatures` | Torch, gallery import, manual capture, camera switch, sharing, JPEG, and PNG visibility. |
| `filters` / `defaultFilter` | Filter choices shown to the user. The default must be one of the enabled filters. |
| `autoCaptureEnabled` | Shows the auto/manual product mode control. Auto capture remains driven by native readiness evidence. |
| `manualCropEnabled` | Enables validated four-corner correction. |
| `multiPageEnabled` | Enables session/review affordances. |
| `debugPanelEnabled` | Shows the separate diagnostics route; it is not overlaid on the preview. |
| `textLabels` | Host-facing labels for brand-sensitive product language. |
| `callbacks` | Page-captured, export-complete, and runtime-error events. |

Unsupported advanced filters are not silently applied. The reference scalar runtime exposes
Original, Auto, Clean, Color, Grayscale, and Black & White. Receipt, Book, Whiteboard, and Photo
Document remain configuration/UI-ready but must remain marked unavailable until the native runtime
implements them.
