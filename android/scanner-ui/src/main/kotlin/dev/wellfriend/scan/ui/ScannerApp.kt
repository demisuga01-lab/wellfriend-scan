package dev.wellfriend.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import dev.wellfriend.scan.core.ScannerState
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

private enum class ProductDestination { SCANNER, FILTERS, EXPORT, SETTINGS, DIAGNOSTICS }

/** Product shell: Kotlin owns presentation and session intent; native perception owns image decisions. */
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
    var destination by remember { mutableStateOf(ProductDestination.SCANNER) }
    var configuredOptions by remember(options) { mutableStateOf(options) }
    var lastCapturedPageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.activePageId, state.session.pages.size) {
        val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
        if (page != null && page.id != lastCapturedPageId && state.state == ScannerState.REVIEWING_PAGE) {
            lastCapturedPageId = page.id
            configuredOptions.callbacks.onPageCaptured?.invoke(page)
        }
    }
    LaunchedEffect(state.error) { state.error?.let { configuredOptions.callbacks.onRuntimeError?.invoke(it) } }

    MaterialTheme {
        when {
            destination == ProductDestination.SETTINGS -> SettingsScreen(configuredOptions, { configuredOptions = it }) { destination = ProductDestination.SCANNER }
            destination == ProductDestination.DIAGNOSTICS -> DiagnosticsScreen(state, cameraController, { destination = ProductDestination.SCANNER }, onExportDebug)
            destination == ProductDestination.EXPORT -> ExportScreen(state, configuredOptions, { destination = ProductDestination.SCANNER }, onExportDebug) { page, format ->
                onExportPage(page, format).onSuccess { configuredOptions.callbacks.onExportCompleted?.invoke(state.session, configuredOptions.exportOptions) }
            }
            destination == ProductDestination.FILTERS -> FilterScreen(state, configuredOptions, runtimeImages, scanController) { destination = ProductDestination.SCANNER }
            state.state == ScannerState.EDITING_CROP -> CropEditorScreen(state, runtimeImages, scanController)
            state.state in setOf(ScannerState.REVIEWING_PAGE, ScannerState.APPLYING_FILTER, ScannerState.PAGE_ACCEPTED, ScannerState.EXPORTING) -> ReviewScreen(
                state, configuredOptions, runtimeImages, scanController,
                onContinueScanning = scanController::continueScanning,
                onOpenFilters = { destination = ProductDestination.FILTERS },
                onOpenExport = { destination = ProductDestination.EXPORT },
                onOpenSettings = { destination = ProductDestination.SETTINGS },
                onOpenDiagnostics = { destination = ProductDestination.DIAGNOSTICS },
            )
            else -> CameraScreen(
                state, configuredOptions, scanController, cameraController, onStartCamera, onManualCapture, onGalleryImport,
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
    val cameraDiagnostics by cameraController.diagnostics.collectAsState()
    val cameraEnabled = state.state !in setOf(ScannerState.IDLE, ScannerState.REQUESTING_PERMISSION, ScannerState.ERROR)
    val accent = Color(options.theme.accentArgb.toInt())

    LaunchedEffect(autoCaptureArmed) { scanController.setAutoCaptureArmed(autoCaptureArmed) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(cameraController, cameraEnabled, Modifier.fillMaxSize())
        state.analysis?.let { analysis ->
            LiveDocumentOverlay(analysis.overlayGeometry, analysis.inputSize, analysis.rotationDegrees, analysis.mirrored, Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(options.theme.brandName, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(options.textLabels.cameraTitle, color = Color(0xFFE2E7EA))
                }
                RuntimeChip(state, cameraDiagnostics.useCasesBound)
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                GuidanceCard(state.guidance, state.analysis?.captureReadinessScore)
                state.error?.let { CameraErrorCard(it, onStartCamera) }
            }
            val controlModifier = if (options.theme.controlPlacement == ScannerControlPlacement.BOTTOM_BAR) {
                Modifier.fillMaxWidth()
            } else {
                // A host can keep the capture controls clear of a branded bottom sheet. The
                // controls remain inside this safe-area-aware camera column; only their width
                // and horizontal placement change.
                Modifier.fillMaxWidth(0.82f).align(Alignment.End)
            }
            Surface(color = Color(0xED101419), shape = RoundedCornerShape(28.dp), modifier = controlModifier) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${state.session.pages.size} page${if (state.session.pages.size == 1) "" else "s"}", color = Color.White)
                        Text(if (autoCaptureArmed) "Auto capture armed" else "Manual capture", color = Color(0xFFB8C3C9))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                        if (options.enabledFeatures.torch) ControlButton(if (torchEnabled) "Torch on" else "Torch") { torchEnabled = !torchEnabled; cameraController.setTorch(torchEnabled) }
                        if (options.enabledFeatures.galleryImport) ControlButton(options.textLabels.import, onGalleryImport)
                        if (options.enabledFeatures.autoCapture) ControlButton(if (autoCaptureArmed) "Auto" else "Manual") { autoCaptureArmed = !autoCaptureArmed }
                        if (options.enabledFeatures.cameraSwitch) ControlButton("Switch") { cameraController.switchCamera() }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onManualCapture,
                        enabled = options.enabledFeatures.manualCapture && cameraDiagnostics.useCasesBound,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.size(82.dp),
                    ) {
                        Text(options.textLabels.capture, textAlign = TextAlign.Center)
                    }
                    if (options.debugPanelEnabled) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = onOpenDiagnostics) { Text("Runtime diagnostics") }
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
    onOpenDiagnostics: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val active = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F8)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Review scan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${state.session.pages.size} page${if (state.session.pages.size == 1) "" else "s"} in this session")
            }
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Spacer(Modifier.height(12.dp))
        PageStrip(state, scanController)
        Spacer(Modifier.height(12.dp))
        if (active == null) {
            EmptyReview(onContinueScanning)
            return@Column
        }
        Card(modifier = Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF20242A))) {
            RuntimeImagePreview(active.displayPage?.uri ?: active.sourceUri, runtimeImages, Modifier.fillMaxSize().padding(10.dp), rotationDegrees = active.rotationDegrees)
        }
        Spacer(Modifier.height(12.dp))
        Text("${active.filter.name.replace('_', ' ')}  •  ${active.processingStatus.name.lowercase()}")
        active.diagnostics.lastOrNull()?.let { Text(it, color = Color(0xFF59636A), style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { scope.launch { runCatching { scanController.reconstructPage(active.id) } } }) { Text(if (active.canonicalPage == null) "Reconstruct" else "Rebuild") }
            if (options.manualCropEnabled) OutlinedButton(onClick = { scanController.beginManualCrop(active.id) }) { Text("Crop") }
            OutlinedButton(onClick = { scanController.rotatePage(active.id) }) { Text("Rotate") }
            OutlinedButton(onClick = { scanController.retakePage(active.id) }) { Text("Retake") }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = onOpenFilters) { Text("Filters") }
            OutlinedButton(onClick = onOpenExport) { Text("Export") }
            OutlinedButton(onClick = { scanController.deletePage(active.id) }) { Text("Delete") }
        }
        val activeIndex = state.session.pages.indexOfFirst { it.id == active.id }
        if (state.session.pages.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = activeIndex > 0,
                    onClick = { scanController.reorderPages(activeIndex, activeIndex - 1) },
                ) { Text("Move earlier") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = activeIndex in 0 until state.session.pages.lastIndex,
                    onClick = { scanController.reorderPages(activeIndex, activeIndex + 1) },
                ) { Text("Move later") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = options.multiPageEnabled || state.session.pages.isEmpty(),
            onClick = onContinueScanning,
        ) { Text(options.textLabels.continueScanning) }
        if (options.debugPanelEnabled) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenDiagnostics) { Text("Runtime diagnostics") }
    }
}

@Composable
private fun CropEditorScreen(state: ScannerUiState, runtimeImages: NativeRuntimeImageStore, scanController: ScanController) {
    val scope = rememberCoroutineScope()
    val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    val geometry = page?.effectiveGeometry
    if (page == null || geometry == null) {
        ErrorScreen("A valid manual crop is required before reconstruction.") { scanController.continueScanning() }
        return
    }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F8)).padding(16.dp)) {
        Text("Adjust crop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Drag the large corner handles. Your crop is validated before Rust reconstruction.")
        Spacer(Modifier.height(12.dp))
        ManualCropEditor(
            geometry = geometry,
            onApply = { corners -> scanController.applyManualCrop(page.id, corners).onSuccess { scope.launch { runCatching { scanController.reconstructPage(page.id) } } } },
            onReset = { scanController.resetToDetectedCrop(page.id) },
            onCancel = { scanController.cancelManualCrop(page.id) },
            background = { RuntimeImagePreview(page.sourceUri, runtimeImages, Modifier.fillMaxSize(), "Source image for manual crop", page.rotationDegrees) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scanController.rotatePage(page.id) }) { Text("Rotate 90°") }
            OutlinedButton(onClick = { scanController.cancelManualCrop(page.id) }) { Text("Back to review") }
        }
    }
}

@Composable
private fun FilterScreen(state: ScannerUiState, options: WellfriendScannerOptions, runtimeImages: NativeRuntimeImageStore, scanController: ScanController, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val active = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    var showingOriginal by remember(active?.id) { mutableStateOf(false) }
    if (active == null) {
        ErrorScreen("Choose a page before selecting a filter.", onBack)
        return
    }
    val previewUri = if (showingOriginal) active.canonicalPage?.uri ?: active.sourceUri else active.displayPage?.uri ?: active.sourceUri
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F8)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(if (showingOriginal) "Before" else "Current: ${active.filter.name.replace('_', ' ')}")
            }
            OutlinedButton(onClick = { showingOriginal = !showingOriginal }) { Text(if (showingOriginal) "Show current" else "Compare before") }
        }
        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF20242A))) {
            RuntimeImagePreview(previewUri, runtimeImages, Modifier.fillMaxSize().padding(10.dp), rotationDegrees = active.rotationDegrees)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options.filters, key = { it.name }) { preset ->
                FilterChoice(preset, active.filter == preset) {
                    scope.launch {
                        runCatching {
                            if (active.canonicalPage == null) scanController.reconstructPage(active.id)
                            scanController.applyFilter(active.id, preset)
                        }
                        showingOriginal = false
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = state.session.pages.size > 1, onClick = { scope.launch { runCatching { scanController.applyFilterToAll(active.filter) } } }) {
            Text("Apply ${active.filter.name.replace('_', ' ')} to all pages")
        }
        Spacer(Modifier.height(8.dp))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Done") }
    }
}

@Composable
private fun ExportScreen(state: ScannerUiState, options: WellfriendScannerOptions, onBack: () -> Unit, onExportDebug: () -> Unit, onExportPage: (ScanPage, ExportFormat) -> Result<File>) {
    val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    var message by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F8)).padding(16.dp)) {
        Text("Export", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Exports only registered processed pixels. Debug JSON excludes image bytes.")
        Spacer(Modifier.height(16.dp))
        if (page != null && options.enabledFeatures.jpegExport && options.enabledFeatures.share) Button(modifier = Modifier.fillMaxWidth(), onClick = { message = onExportPage(page, ExportFormat.JPEG).fold({ "JPEG ready to share: ${it.name}" }, { it.message ?: "JPEG export failed" }) }) { Text("Share JPEG") }
        if (page != null && options.enabledFeatures.pngExport && options.enabledFeatures.share) OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { message = onExportPage(page, ExportFormat.PNG).fold({ "PNG ready to share: ${it.name}" }, { it.message ?: "PNG export failed" }) }) { Text("Share PNG") }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onExportDebug(); message = "Debug JSON saved privately to the app export folder" }) { Text("Export debug JSON") }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("PDF export: coming soon. No PDF is generated by this scalar reference runtime.", modifier = Modifier.padding(12.dp)) }
        message?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = Color(0xFF2B5D45)) }
        Spacer(Modifier.weight(1f))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Back to review") }
    }
}

@Composable
private fun SettingsScreen(options: WellfriendScannerOptions, onChange: (WellfriendScannerOptions) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF6F7F8)).padding(16.dp)) {
        Text("Scanner settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("These controls demonstrate the same host-facing options exposed by the SDK.")
        Spacer(Modifier.height(12.dp))
        SettingToggle("Show gallery import", options.enabledFeatures.galleryImport) { onChange(options.copy(enabledFeatures = options.enabledFeatures.copy(galleryImport = it))) }
        SettingToggle("Show torch", options.enabledFeatures.torch) { onChange(options.copy(enabledFeatures = options.enabledFeatures.copy(torch = it))) }
        SettingToggle("Manual crop", options.manualCropEnabled) { onChange(options.copy(manualCropEnabled = it)) }
        SettingToggle("Multi-page session", options.multiPageEnabled) { onChange(options.copy(multiPageEnabled = it)) }
        SettingToggle("Runtime diagnostics entry", options.debugPanelEnabled) { onChange(options.copy(debugPanelEnabled = it)) }
        Spacer(Modifier.height(16.dp))
        Text("Enabled filters", fontWeight = FontWeight.Bold)
        Text(options.filters.joinToString { it.name.replace('_', ' ') })
        Text("Theme: ${options.theme.mode.name.lowercase()} • controls: ${options.theme.controlPlacement.name.lowercase()}")
        Spacer(Modifier.weight(1f))
        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Done") }
    }
}

@Composable
private fun DiagnosticsScreen(state: ScannerUiState, cameraController: CameraXScannerController, onBack: () -> Unit, onExportDebug: () -> Unit) {
    val camera by cameraController.diagnostics.collectAsState()
    val artifact = NativeRuntimeArtifactDiagnostics.snapshot()
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF101419)).padding(16.dp)) {
        Text("Runtime diagnostics", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
        DiagnosticsLine("Runtime", state.analysis?.engineMode?.name ?: "PENDING")
        DiagnosticsLine("Mock used", if (state.analysis?.engineMode == PerceptionEngineMode.DEV_JVM_MOCK) "true (development only)" else "false")
        DiagnosticsLine("Artifact SHA", artifact.sourceSha ?: "unavailable")
        DiagnosticsLine("Artifact schema", artifact.schemaVersion?.toString() ?: "unknown")
        DiagnosticsLine("Camera bound", camera.useCasesBound.toString())
        DiagnosticsLine("Preview surface", camera.previewSurfaceAttached.toString())
        DiagnosticsLine("Frames", camera.frameCount.toString())
        DiagnosticsLine("Last frame", camera.lastFrame ?: "none")
        DiagnosticsLine("Guidance", state.guidance.joinToString())
        DiagnosticsLine("Native status", NativeLibraryLoader.status.diagnostic)
        camera.lastError?.let { DiagnosticsLine("Camera error", it, Color(0xFFFFB4AB)) }
        state.error?.let { DiagnosticsLine("Runtime error", it, Color(0xFFFFB4AB)) }
        Spacer(Modifier.weight(1f))
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onExportDebug) { Text("Export debug JSON", color = Color.White) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) { Text("Back to scanner") }
    }
}

@Composable
private fun PageStrip(state: ScannerUiState, scanController: ScanController) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(state.session.pages, key = { it.id }) { page ->
            val selected = page.id == state.activePageId
            Surface(
                shape = RoundedCornerShape(12.dp), color = if (selected) Color(0xFFDCF8E9) else Color.White,
                modifier = Modifier.border(1.dp, if (selected) Color(0xFF41A86D) else Color(0xFFCDD3D7), RoundedCornerShape(12.dp)).clickable { scanController.selectReviewPage(page.id) },
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Page ${state.session.pages.indexOf(page) + 1}", fontWeight = FontWeight.Bold)
                    Text(page.filter.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun FilterChoice(preset: FilterPreset, selected: Boolean, onSelect: () -> Unit) {
    val implemented = preset in FilterPreset.defaultProductFilters
    Surface(
        color = if (selected) Color(0xFFDCF8E9) else Color.White, shape = RoundedCornerShape(14.dp),
        modifier = Modifier.border(1.dp, if (selected) Color(0xFF41A86D) else Color(0xFFCDD3D7), RoundedCornerShape(14.dp)).clickable(enabled = implemented, onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(preset.name.replace('_', ' '), fontWeight = FontWeight.Medium)
            Text(if (implemented) "Native scalar" else "Coming soon", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun GuidanceCard(guidance: List<CaptureGuidance>, confidence: Float?) {
    val text = guidance.firstOrNull()?.let(::guidanceText) ?: "Searching for document"
    Surface(color = Color(0xDD11161B), shape = RoundedCornerShape(20.dp)) {
        Text(if (confidence == null) text else "$text • ${(confidence * 100).toInt()}%", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun RuntimeChip(state: ScannerUiState, cameraBound: Boolean) {
    val native = state.analysis?.engineMode != PerceptionEngineMode.DEV_JVM_MOCK && NativeLibraryLoader.status.available
    Surface(color = if (native) Color(0xCC173F2B) else Color(0xCC5F251F), shape = RoundedCornerShape(16.dp)) {
        Text(if (native) "Native • ${if (cameraBound) "live" else "starting"}" else "Runtime unavailable", color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CameraErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)), modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Camera preview failed", fontWeight = FontWeight.Bold, color = Color(0xFF8B1E15))
            Text("Reason: $message")
            Text("Action: retry the camera or check permission and hardware availability.")
            Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) { Text("Retry camera") }
        }
    }
}

@Composable private fun ControlButton(label: String, onClick: () -> Unit) { OutlinedButton(onClick = onClick) { Text(label, textAlign = TextAlign.Center) } }
@Composable private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.weight(1f)); Switch(checked, onCheckedChange) } }
@Composable private fun DiagnosticsLine(label: String, value: String, color: Color = Color(0xFFE1E8EC)) { Text("$label: $value", color = color, modifier = Modifier.padding(vertical = 3.dp)) }
@Composable private fun EmptyReview(onContinueScanning: () -> Unit) { Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("No pages in this session"); Button(onClick = onContinueScanning, modifier = Modifier.padding(top = 12.dp)) { Text("Open camera") } } }
@Composable private fun ErrorScreen(message: String, onBack: () -> Unit) { Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(message, textAlign = TextAlign.Center); Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Back") } } }

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
