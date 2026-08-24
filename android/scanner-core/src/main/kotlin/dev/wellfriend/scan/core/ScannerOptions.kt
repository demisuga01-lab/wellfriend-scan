package dev.wellfriend.scan.core

/** Product-facing configuration that lets an SDK host brand and constrain the scanner without changing perception. */
data class WellfriendScannerOptions(
    val theme: ScannerTheme = ScannerTheme(),
    val mode: ScannerMode = ScannerMode.DOCUMENT,
    val enabledFeatures: ScannerFeatureSet = ScannerFeatureSet(),
    val filters: List<FilterPreset> = FilterPreset.defaultProductFilters,
    val defaultFilter: FilterPreset = FilterPreset.ORIGINAL,
    // Manual is the safe product default. Hosts can opt into auto capture and users can
    // arm it from the camera screen after confirming the live preview is stable.
    val autoCaptureEnabled: Boolean = false,
    val manualCropEnabled: Boolean = true,
    val multiPageEnabled: Boolean = true,
    val debugPanelEnabled: Boolean = true,
    val exportOptions: ExportOptions = ExportOptions(),
    val textLabels: ScannerTextLabels = ScannerTextLabels(),
    val callbacks: ScannerCallbacks = ScannerCallbacks(),
) {
    init {
        require(defaultFilter in filters) { "default filter must be enabled" }
        require(filters.isNotEmpty()) { "at least one filter must be enabled" }
    }
}

enum class ScannerMode { DOCUMENT, RECEIPT, ID_CARD, WHITEBOARD }
enum class ScannerThemeMode { SYSTEM, LIGHT, DARK }
enum class ScannerControlPlacement { BOTTOM_BAR, FLOATING_CONTROLS }

data class ScannerTheme(
    val mode: ScannerThemeMode = ScannerThemeMode.SYSTEM,
    val brandName: String = "Wellfriend Scan",
    val accentArgb: Long = 0xFF41D68A,
    val controlPlacement: ScannerControlPlacement = ScannerControlPlacement.BOTTOM_BAR,
)

data class ScannerFeatureSet(
    val torch: Boolean = true,
    val galleryImport: Boolean = true,
    val autoCapture: Boolean = true,
    val manualCapture: Boolean = true,
    val cameraSwitch: Boolean = true,
    val share: Boolean = true,
    val jpegExport: Boolean = true,
    val pngExport: Boolean = true,
)

data class ScannerTextLabels(
    val cameraTitle: String = "Scan a document",
    val capture: String = "Capture",
    val import: String = "Import",
    val continueScanning: String = "Scan another page",
    val runtimeUnavailable: String = "Native scanner runtime is unavailable",
)

/** Host callbacks are optional and contain only product events, never raw camera frames or image bytes. */
data class ScannerCallbacks(
    val onPageCaptured: ((ScanPage) -> Unit)? = null,
    val onExportCompleted: ((ScanSession, ExportOptions) -> Unit)? = null,
    val onRuntimeError: ((String) -> Unit)? = null,
)
