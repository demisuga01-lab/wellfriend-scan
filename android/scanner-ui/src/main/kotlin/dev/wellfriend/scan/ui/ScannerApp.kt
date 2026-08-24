package dev.wellfriend.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.wellfriend.scan.core.CaptureGuidance
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.ScanPage
import dev.wellfriend.scan.core.ScannerControlPlacement
import dev.wellfriend.scan.core.ScannerFeatureSet
import dev.wellfriend.scan.core.ScannerState
import dev.wellfriend.scan.core.ScannerTheme
import dev.wellfriend.scan.core.WellfriendScannerOptions
import dev.wellfriend.scan.export.ExportFormat
import dev.wellfriend.scan.perception.NativeLibraryLoader
import dev.wellfriend.scan.perception.NativeRuntimeArtifactDiagnostics
import dev.wellfriend.scan.perception.NativeRuntimeImageStore
import dev.wellfriend.scan.perception.PerceptionEngineMode
import dev.wellfriend.scan.perception.ScanController
import dev.wellfriend.scan.perception.ScannerUiState
import dev.wellfriend.scan.ui.camera.CameraPreview
import dev.wellfriend.scan.ui.camera.CameraXScannerController
import dev.wellfriend.scan.ui.overlay.LiveDocumentOverlay
import dev.wellfriend.scan.ui.overlay.ManualCropEditor
import java.io.File
import kotlinx.coroutines.launch

private enum class ProductDestination { CAMERA, FILTERS, EXPORT, SETTINGS, DIAGNOSTICS }

private val AppBackground = Color(0xFFF7F7F5)
private val Ink = Color(0xFF151615)
private val MutedInk = Color(0xFF666A65)
private val CameraPanel = Color(0xF2171917)
private val ReviewStates = setOf(
    ScannerState.REVIEWING_PAGE,
    ScannerState.APPLYING_FILTER,
    ScannerState.PAGE_ACCEPTED,
    ScannerState.EXPORTING,
)

/**
 * Restrained product shell. Kotlin owns navigation and configuration; native perception owns
 * all image analysis, reconstruction and implemented scalar filters.
 */
@Composable
fun WellfriendScannerApp(
    scanController: ScanController,
    cameraController: CameraXScannerController,
    runtimeImages: NativeRuntimeImageStore,
    options: WellfriendScannerOptions = WellfriendScannerOptions(),
    onStartCamera: () -> Unit,
    onManualCapture: () -> Unit,
    onGalleryImport: () -> Unit,
    onExportDebug: () -> Unit,
    onExportPage: (ScanPage, ExportFormat) -> Result<File>,
) {
    val state by scanController.state.collectAsState()
    var destination by remember { mutableStateOf(ProductDestination.CAMERA) }
    var configuredOptions by remember(options) { mutableStateOf(options) }
    var lastCapturedPageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.activePageId, state.session.pages.size, state.state) {
        val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
        if (page != null && page.id != lastCapturedPageId && state.state == ScannerState.REVIEWING_PAGE) {
            lastCapturedPageId = page.id
            configuredOptions.callbacks.onPageCaptured?.invoke(page)
        }
    }
    LaunchedEffect(state.error) { state.error?.let { configuredOptions.callbacks.onRuntimeError?.invoke(it) } }

    MaterialTheme {
        when {
            destination == ProductDestination.SETTINGS -> SettingsScreen(
                options = configuredOptions,
                onChange = { configuredOptions = it },
                onBack = { destination = ProductDestination.CAMERA },
                onOpenDiagnostics = { destination = ProductDestination.DIAGNOSTICS },
            )
            destination == ProductDestination.DIAGNOSTICS -> DiagnosticsScreen(
                state = state,
                cameraController = cameraController,
                onBack = { destination = ProductDestination.CAMERA },
                onExportDebug = onExportDebug,
            )
            destination == ProductDestination.EXPORT -> ExportScreen(
                state = state,
                options = configuredOptions,
                onBack = { destination = ProductDestination.CAMERA },
                onExportDebug = onExportDebug,
                onExportPage = { page, format ->
                    onExportPage(page, format).onSuccess {
                        configuredOptions.callbacks.onExportCompleted?.invoke(state.session, configuredOptions.exportOptions)
                    }
                },
            )
            destination == ProductDestination.FILTERS -> FilterScreen(
                state = state,
                options = configuredOptions,
                runtimeImages = runtimeImages,
                scanController = scanController,
                onBack = { destination = ProductDestination.CAMERA },
            )
            state.state == ScannerState.EDITING_CROP -> CropScreen(state, runtimeImages, scanController)
            state.state in ReviewStates -> ReviewScreen(
                state = state,
                options = configuredOptions,
                runtimeImages = runtimeImages,
                scanController = scanController,
                onContinueScanning = scanController::continueScanning,
                onOpenFilters = { destination = ProductDestination.FILTERS },
                onOpenExport = { destination = ProductDestination.EXPORT },
                onOpenSettings = { destination = ProductDestination.SETTINGS },
            )
            else -> CameraScreen(
                state = state,
                options = configuredOptions,
                scanController = scanController,
                cameraController = cameraController,
                onStartCamera = onStartCamera,
                onManualCapture = onManualCapture,
                onGalleryImport = onGalleryImport,
                onOpenSettings = { destination = ProductDestination.SETTINGS },
                onOpenDiagnostics = { destination = ProductDestination.DIAGNOSTICS },
            )
        }
    }
}

@Composable
private fun CameraScreen(
    state: ScannerUiState,
    options: WellfriendScannerOptions,
    scanController: ScanController,
    cameraController: CameraXScannerController,
    onStartCamera: () -> Unit,
    onManualCapture: () -> Unit,
    onGalleryImport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    var torchEnabled by remember { mutableStateOf(false) }
    var autoCaptureArmed by remember(options.autoCaptureEnabled) { mutableStateOf(options.autoCaptureEnabled) }
    val camera by cameraController.diagnostics.collectAsState()
    val accent = Color(options.theme.accentArgb.toInt())
    val cameraEnabled = state.state !in setOf(ScannerState.IDLE, ScannerState.REQUESTING_PERMISSION, ScannerState.ERROR)
    LaunchedEffect(autoCaptureArmed) { scanController.setAutoCaptureArmed(autoCaptureArmed) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(cameraController, cameraEnabled, Modifier.fillMaxSize())
        state.analysis?.let { analysis ->
            LiveDocumentOverlay(
                geometry = analysis.overlayGeometry,
                imageSize = analysis.inputSize,
                rotationDegrees = analysis.rotationDegrees,
                mirrored = analysis.mirrored,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(options.theme.brandName, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(options.textLabels.cameraTitle, color = Color(0xFFD3D7D2), style = MaterialTheme.typography.labelMedium)
                }
                RuntimeChip(state, camera.useCasesBound)
                Spacer(Modifier.width(8.dp))
                CompactAction("Settings", onOpenSettings)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GuidancePill(state.guidance, state.analysis?.captureReadinessScore)
                state.error?.let { CameraFailure(it, onStartCamera) }
            }
            val controlModifier = if (options.theme.controlPlacement == ScannerControlPlacement.BOTTOM_BAR) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth(0.78f).align(Alignment.End)
            }
            Surface(modifier = controlModifier, color = CameraPanel, shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${state.session.pages.size} page${if (state.session.pages.size == 1) "" else "s"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text(if (autoCaptureArmed) "Auto capture" else "Manual capture", color = Color(0xFFB9C0BA), style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (options.enabledFeatures.galleryImport) CompactAction(options.textLabels.import, onGalleryImport)
                            if (options.enabledFeatures.torch) CompactAction(if (torchEnabled) "Flash on" else "Flash") {
                                torchEnabled = !torchEnabled
                                cameraController.setTorch(torchEnabled)
                            }
                        }
                        Button(
                            onClick = onManualCapture,
                            enabled = options.enabledFeatures.manualCapture && camera.useCasesBound,
                            modifier = Modifier.size(76.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        ) { Text(options.textLabels.capture, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (options.enabledFeatures.autoCapture) CompactAction(if (autoCaptureArmed) "Auto" else "Manual") { autoCaptureArmed = !autoCaptureArmed }
                            if (options.enabledFeatures.cameraSwitch) CompactAction("Flip") { cameraController.switchCamera() }
                        }
                    }
                    if (options.debugPanelEnabled) {
                        Text("Runtime details", color = Color(0xFFB9C0BA), style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable(onClick = onOpenDiagnostics).padding(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewScreen(
    state: ScannerUiState,
    options: WellfriendScannerOptions,
    runtimeImages: NativeRuntimeImageStore,
    scanController: ScanController,
    onContinueScanning: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val active = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    if (active == null) {
        EmptyReview(onContinueScanning)
        return
    }
    val activeIndex = state.session.pages.indexOfFirst { it.id == active.id }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        MinimalTopBar("Review", "${state.session.pages.size} page${if (state.session.pages.size == 1) "" else "s"}", "Settings", onOpenSettings)
        Spacer(Modifier.height(12.dp))
        PageStrip(state, scanController)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1A))) {
            RuntimeImagePreview(active.displayPage?.uri ?: active.sourceUri, runtimeImages, Modifier.fillMaxSize().padding(8.dp), rotationDegrees = active.rotationDegrees)
        }
        Spacer(Modifier.height(10.dp))
        Text(active.filter.name.replace('_', ' ') + " · " + active.processingStatus.name.lowercase(), color = MutedInk, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Crop", options.manualCropEnabled, Modifier.weight(1f)) { scanController.beginManualCrop(active.id) }
            SecondaryAction("Rotate", modifier = Modifier.weight(1f)) { scanController.rotatePage(active.id) }
            SecondaryAction("Filters", modifier = Modifier.weight(1f), onClick = onOpenFilters)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Retake", modifier = Modifier.weight(1f)) { scanController.retakePage(active.id) }
            SecondaryAction("Delete", modifier = Modifier.weight(1f)) { scanController.deletePage(active.id) }
            SecondaryAction("Export", modifier = Modifier.weight(1f), onClick = onOpenExport)
        }
        if (state.session.pages.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction("Move earlier", activeIndex > 0, Modifier.weight(1f)) { scanController.reorderPages(activeIndex, activeIndex - 1) }
                SecondaryAction("Move later", activeIndex in 0 until state.session.pages.lastIndex, Modifier.weight(1f)) { scanController.reorderPages(activeIndex, activeIndex + 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = options.multiPageEnabled || state.session.pages.isEmpty(), onClick = onContinueScanning) { Text(options.textLabels.continueScanning) }
    }
}

@Composable
private fun CropScreen(state: ScannerUiState, runtimeImages: NativeRuntimeImageStore, scanController: ScanController) {
    val scope = rememberCoroutineScope()
    val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    val geometry = page?.effectiveGeometry
    if (page == null || geometry == null) {
        ErrorScreen("A valid crop is required before reconstruction.") { scanController.continueScanning() }
        return
    }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        MinimalTopBar("Adjust crop", "Drag a corner, then apply", "Rotate", { scanController.rotatePage(page.id) }, inverse = true)
        Spacer(Modifier.height(12.dp))
        ManualCropEditor(
            geometry = geometry,
            onApply = { corners -> scanController.applyManualCrop(page.id, corners).onSuccess { scope.launch { runCatching { scanController.reconstructPage(page.id) } } } },
            onReset = { scanController.resetToDetectedCrop(page.id) },
            onCancel = { scanController.cancelManualCrop(page.id) },
            background = { RuntimeImagePreview(page.sourceUri, runtimeImages, Modifier.fillMaxSize(), "Original page for crop adjustment", page.rotationDegrees) },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

@Composable
private fun FilterScreen(
    state: ScannerUiState,
    options: WellfriendScannerOptions,
    runtimeImages: NativeRuntimeImageStore,
    scanController: ScanController,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val active = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    var showingBefore by remember(active?.id) { mutableStateOf(false) }
    if (active == null) {
        ErrorScreen("Choose a page before selecting a filter.", onBack)
        return
    }
    LaunchedEffect(active.id) {
        if (active.canonicalPage == null) runCatching { scanController.reconstructPage(active.id) }
        if (active.filter == FilterPreset.ORIGINAL && options.defaultFilter != FilterPreset.ORIGINAL) runCatching { scanController.applyFilter(active.id, options.defaultFilter) }
    }
    val previewUri = if (showingBefore) active.canonicalPage?.uri ?: active.sourceUri else active.displayPage?.uri ?: active.sourceUri
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        MinimalTopBar(
            "Filters",
            if (showingBefore) "Before" else active.filter.name.replace('_', ' '),
            if (showingBefore) "Current" else "Before",
            onAction = { showingBefore = !showingBefore },
        )
        Spacer(Modifier.height(12.dp))
        Card(Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1A))) {
            RuntimeImagePreview(previewUri, runtimeImages, Modifier.fillMaxSize().padding(8.dp), rotationDegrees = active.rotationDegrees)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.filters.forEach { preset ->
                FilterChip(preset, active.filter == preset) {
                    scope.launch {
                        runCatching {
                            if (active.canonicalPage == null) scanController.reconstructPage(active.id)
                            scanController.applyFilter(active.id, preset)
                        }
                        showingBefore = false
                    }
                }
            }
        }
        if (state.session.pages.size > 1) {
            Spacer(Modifier.height(8.dp))
            SecondaryAction("Apply ${active.filter.name.replace('_', ' ')} to all pages", modifier = Modifier.fillMaxWidth()) { scope.launch { runCatching { scanController.applyFilterToAll(active.filter) } } }
        }
        Spacer(Modifier.height(10.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Done") }
    }
}

@Composable
private fun ExportScreen(
    state: ScannerUiState,
    options: WellfriendScannerOptions,
    onBack: () -> Unit,
    onExportDebug: () -> Unit,
    onExportPage: (ScanPage, ExportFormat) -> Result<File>,
) {
    val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        MinimalTopBar("Export", "The current page only", "Back", onBack)
        Spacer(Modifier.height(24.dp))
        Text("Share a processed image", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
        Text("Only native runtime output is exported. Debug JSON does not include image bytes.", color = MutedInk)
        Spacer(Modifier.height(16.dp))
        if (page != null && options.enabledFeatures.share && options.enabledFeatures.jpegExport) Button(modifier = Modifier.fillMaxWidth(), onClick = { message = onExportPage(page, ExportFormat.JPEG).fold({ "JPEG ready to share" }, { it.message ?: "JPEG export failed" }) }) { Text("Share JPEG") }
        if (page != null && options.enabledFeatures.share && options.enabledFeatures.pngExport) {
            Spacer(Modifier.height(8.dp))
            SecondaryAction("Share PNG", modifier = Modifier.fillMaxWidth()) { message = onExportPage(page, ExportFormat.PNG).fold({ "PNG ready to share" }, { it.message ?: "PNG export failed" }) }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryAction("Export debug JSON", modifier = Modifier.fillMaxWidth(), onClick = { onExportDebug(); message = "Debug JSON exported" })
        Spacer(Modifier.height(16.dp))
        Surface(color = Color(0xFFECEDE9), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text("PDF export is not implemented in this scalar reference runtime.", color = MutedInk, modifier = Modifier.padding(14.dp)) }
        message?.let { Text(it, color = Color(0xFF23613B), modifier = Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun SettingsScreen(
    options: WellfriendScannerOptions,
    onChange: (WellfriendScannerOptions) -> Unit,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        MinimalTopBar("Scanner settings", "Applied immediately to this session", "Done", onBack)
        SettingsSection("Appearance")
        SettingToggle("Floating capture controls", options.theme.controlPlacement == ScannerControlPlacement.FLOATING_CONTROLS) {
            onChange(options.copy(theme = options.theme.copy(controlPlacement = if (it) ScannerControlPlacement.FLOATING_CONTROLS else ScannerControlPlacement.BOTTOM_BAR)))
        }
        Text("Accent", color = MutedInk, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            AccentChoice(options.theme, 0xFF41D68AL, "Green", onChange, options)
            AccentChoice(options.theme, 0xFF5288F2L, "Blue", onChange, options)
            AccentChoice(options.theme, 0xFFDD7D4AL, "Orange", onChange, options)
        }
        SettingsSection("Camera controls")
        FeatureToggles(options, onChange)
        SettingsSection("Workflow")
        SettingToggle("Start with auto capture", options.autoCaptureEnabled) { onChange(options.copy(autoCaptureEnabled = it)) }
        SettingToggle("Manual crop", options.manualCropEnabled) { onChange(options.copy(manualCropEnabled = it)) }
        SettingToggle("Multi-page session", options.multiPageEnabled) { onChange(options.copy(multiPageEnabled = it)) }
        SettingToggle("Show runtime details", options.debugPanelEnabled) { onChange(options.copy(debugPanelEnabled = it)) }
        SettingsSection("Filters")
        FilterPreset.defaultProductFilters.forEach { preset ->
            SettingToggle(preset.name.replace('_', ' '), preset in options.filters) { enabled -> options.withFilterEnabled(preset, enabled)?.let(onChange) }
        }
        Text("Default filter", color = MutedInk, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            options.filters.forEach { preset ->
                FilterChip(preset, options.defaultFilter == preset) { onChange(options.copy(defaultFilter = preset)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (options.debugPanelEnabled) SecondaryAction("Open runtime diagnostics", modifier = Modifier.fillMaxWidth(), onClick = onOpenDiagnostics)
    }
}

@Composable
private fun DiagnosticsScreen(state: ScannerUiState, cameraController: CameraXScannerController, onBack: () -> Unit, onExportDebug: () -> Unit) {
    val camera by cameraController.diagnostics.collectAsState()
    val artifact = NativeRuntimeArtifactDiagnostics.snapshot()
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        MinimalTopBar("Runtime details", "Technical information", "Done", onBack)
        Spacer(Modifier.height(12.dp))
        DiagnosticCard("Runtime", state.analysis?.engineMode?.name ?: "PENDING")
        DiagnosticCard("Mock used", if (state.analysis?.engineMode == PerceptionEngineMode.DEV_JVM_MOCK) "true (development only)" else "false")
        DiagnosticCard("Artifact SHA", artifact.sourceSha ?: "unavailable")
        DiagnosticCard("Camera bound", camera.useCasesBound.toString())
        DiagnosticCard("Preview surface", camera.previewSurfaceAttached.toString())
        DiagnosticCard("Frames", camera.frameCount.toString())
        DiagnosticCard("Last frame", camera.lastFrame ?: "none")
        DiagnosticCard("Guidance", state.guidance.joinToString())
        DiagnosticCard("Native loader", NativeLibraryLoader.status.diagnostic)
        camera.lastError?.let { DiagnosticCard("Camera error", it, Color(0xFF9C2F24)) }
        state.error?.let { DiagnosticCard("Runtime error", it, Color(0xFF9C2F24)) }
        Spacer(Modifier.height(8.dp))
        SecondaryAction("Export debug JSON", modifier = Modifier.fillMaxWidth(), onClick = onExportDebug)
    }
}

@Composable
private fun PageStrip(state: ScannerUiState, scanController: ScanController) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.session.pages.forEachIndexed { index, page ->
            val selected = page.id == state.activePageId
            Surface(
                color = if (selected) Color(0xFFE0F3E7) else Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, if (selected) Color(0xFF36885A) else Color(0xFFD8DBD6), RoundedCornerShape(14.dp)).clickable { scanController.selectReviewPage(page.id) },
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text("Page ${index + 1}", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(page.filter.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MutedInk)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(preset: FilterPreset, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF1D5E38) else Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.border(1.dp, if (selected) Color(0xFF1D5E38) else Color(0xFFD8DBD6), RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Text(preset.name.replace('_', ' '), color = if (selected) Color.White else Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GuidancePill(guidance: List<CaptureGuidance>, confidence: Float?) {
    val text = guidance.firstOrNull()?.let(::guidanceText) ?: "Looking for a document"
    Surface(color = Color(0xE9151715), shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 300.dp)) {
        Text(text + (confidence?.let { " · ${(it * 100).toInt()}%" } ?: ""), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun RuntimeChip(state: ScannerUiState, cameraBound: Boolean) {
    val native = state.analysis?.engineMode != PerceptionEngineMode.DEV_JVM_MOCK && NativeLibraryLoader.status.available
    Surface(color = if (native) Color(0xD9215636) else Color(0xD7793028), shape = RoundedCornerShape(16.dp)) {
        Text(if (native) if (cameraBound) "Native live" else "Native starting" else "Runtime unavailable", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@Composable
private fun MinimalTopBar(title: String, subtitle: String, action: String, onAction: () -> Unit, inverse: Boolean = false) {
    val titleColor = if (inverse) Color.White else Ink
    val subtitleColor = if (inverse) Color(0xFFD3D7D2) else MutedInk
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = subtitleColor, style = MaterialTheme.typography.labelMedium)
        }
        CompactAction(action, onAction)
    }
}

@Composable
private fun CompactAction(label: String, onClick: () -> Unit) {
    Surface(color = Color(0xD91E211E), contentColor = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SecondaryAction(label: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(modifier = modifier, enabled = enabled, onClick = onClick) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
private fun CameraFailure(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDEDEA)), modifier = Modifier.padding(top = 10.dp).widthIn(max = 340.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("Camera preview failed", color = Color(0xFF96281E), fontWeight = FontWeight.Bold)
            Text(message, color = Ink)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onRetry) { Text("Retry camera") }
        }
    }
}

@Composable
private fun SettingsSection(title: String) { Text(title, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 4.dp)) }

@Composable
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun AccentChoice(theme: ScannerTheme, value: Long, label: String, onChange: (WellfriendScannerOptions) -> Unit, options: WellfriendScannerOptions) {
    val selected = theme.accentArgb == value
    Surface(
        color = Color(value.toInt()),
        shape = CircleShape,
        modifier = Modifier.size(if (selected) 42.dp else 34.dp).border(if (selected) 3.dp else 1.dp, if (selected) Ink else Color.White, CircleShape).clickable { onChange(options.copy(theme = theme.copy(accentArgb = value))) },
    ) { Text(label.take(1), color = Color.White, modifier = Modifier.wrapContentWidth().padding(8.dp), fontWeight = FontWeight.Bold) }
}

@Composable
private fun FeatureToggles(options: WellfriendScannerOptions, onChange: (WellfriendScannerOptions) -> Unit) {
    val features = options.enabledFeatures
    fun update(transform: (ScannerFeatureSet) -> ScannerFeatureSet) = onChange(options.copy(enabledFeatures = transform(features)))
    SettingToggle("Gallery import", features.galleryImport) { enabled -> update { current -> current.copy(galleryImport = enabled) } }
    SettingToggle("Torch", features.torch) { enabled -> update { current -> current.copy(torch = enabled) } }
    SettingToggle("Auto capture control", features.autoCapture) { enabled -> update { current -> current.copy(autoCapture = enabled) } }
    SettingToggle("Manual capture", features.manualCapture) { enabled -> update { current -> current.copy(manualCapture = enabled) } }
    SettingToggle("Camera switch", features.cameraSwitch) { enabled -> update { current -> current.copy(cameraSwitch = enabled) } }
    SettingToggle("Share processed pages", features.share) { enabled -> update { current -> current.copy(share = enabled) } }
    SettingToggle("JPEG export", features.jpegExport) { enabled -> update { current -> current.copy(jpegExport = enabled) } }
    SettingToggle("PNG export", features.pngExport) { enabled -> update { current -> current.copy(pngExport = enabled) } }
}

private fun WellfriendScannerOptions.withFilterEnabled(filter: FilterPreset, enabled: Boolean): WellfriendScannerOptions? {
    val next = if (enabled) (filters + filter).distinct() else filters.filterNot { it == filter }
    if (next.isEmpty()) return null
    return copy(filters = next, defaultFilter = if (defaultFilter in next) defaultFilter else next.first())
}

@Composable
private fun DiagnosticCard(label: String, value: String, valueColor: Color = Ink) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = MutedInk, style = MaterialTheme.typography.labelMedium)
            Text(value, color = valueColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyReview(onContinueScanning: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("No scanned pages yet", style = MaterialTheme.typography.titleLarge, color = Ink)
        Text("Capture or import a document to start a session.", color = MutedInk, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = onContinueScanning, modifier = Modifier.padding(top = 16.dp)) { Text("Open camera") }
    }
}

@Composable
private fun ErrorScreen(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = Ink, textAlign = TextAlign.Center)
        Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
    }
}

private fun guidanceText(guidance: CaptureGuidance): String = when (guidance) {
    CaptureGuidance.NO_DOCUMENT -> "Position a document in view"
    CaptureGuidance.MOVE_CLOSER -> "Move closer"
    CaptureGuidance.MOVE_FARTHER -> "Move farther"
    CaptureGuidance.HOLD_STEADY -> "Hold steady"
    CaptureGuidance.TOO_DARK -> "More light needed"
    CaptureGuidance.TOO_BRIGHT -> "Reduce bright light"
    CaptureGuidance.TOO_BLURRY -> "Image is blurry"
    CaptureGuidance.GLARE_DETECTED -> "Reduce glare"
    CaptureGuidance.DOCUMENT_CUT_OFF -> "Keep all borders in view"
    CaptureGuidance.LOW_CONFIDENCE -> "Keep the page in view"
    CaptureGuidance.LOW_DETECTOR_AGREEMENT -> "Hold still for a clearer page"
    CaptureGuidance.READY -> "Ready to capture"
    CaptureGuidance.CAPTURING -> "Capturing"
}
