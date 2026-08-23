# Wellfriend Android scanner customization

`WellfriendScannerOptions` is the stable Android product-configuration surface. It changes
presentation and product policy only; it never replaces the Rust perception, reconstruction, or
implemented filter pipeline.

```kotlin
val options = WellfriendScannerOptions(
    theme = ScannerTheme(brandName = "Acme Capture", accentArgb = 0xFF2457D6),
    enabledFeatures = ScannerFeatureSet(torch = true, galleryImport = true, share = true),
    filters = listOf(FilterPreset.ORIGINAL, FilterPreset.CLEAN, FilterPreset.GRAYSCALE),
    defaultFilter = FilterPreset.CLEAN,
    manualCropEnabled = true,
    multiPageEnabled = true,
    debugPanelEnabled = false,
    callbacks = ScannerCallbacks(
        onPageCaptured = { page -> /* store page metadata */ },
        onRuntimeError = { message -> /* host error telemetry */ },
    ),
)
```

Hosts can customize brand name, accent, control placement, enabled controls, labels, supported
filter choices, crop/multi-page/debug availability, and product callbacks. Callbacks contain
session/product events, never raw camera frames or unbounded image bytes.

The reference app exposes a Settings screen so the same feature decisions can be exercised on a
device. Release builds still fail closed if the native runtime is unavailable; hiding diagnostics
does not permit a mock fallback.
