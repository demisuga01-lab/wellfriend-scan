# Reconstruction and filters

The app requests reconstruction only through `PerceptionEngine.reconstructPage`, then requests filters through `applyFilter`. MP7 exposes Original, Auto, Clean, Color, Grayscale, and B&W. Receipt, Book, Whiteboard, and PhotoDocument are explicitly experimental/deferred, matching MP4 filter contracts.

The debug engine performs no pixel transforms; it returns diagnostics saying so. A native bridge will ultimately invoke MP4 planar reconstruction and scalar routing/restoration, then later validated models.

The Android review flow renders pixel output returned by the native scalar runtime. Source,
canonical reconstruction, and current filtered output stay in the bounded `NativeRuntimeImageStore`;
the UI never silently substitutes a Kotlin filter implementation. The Filters screen supports
before/current comparison, revert through Original, and applying the selected native preset to all
pages. JPEG and PNG sharing uses only registered runtime pixels; PDF remains an honest placeholder.
