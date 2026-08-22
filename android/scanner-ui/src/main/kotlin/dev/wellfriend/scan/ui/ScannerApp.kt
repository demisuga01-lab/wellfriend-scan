package dev.wellfriend.scan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.ScanPage
import dev.wellfriend.scan.core.ScannerState
import dev.wellfriend.scan.perception.ScanController
import dev.wellfriend.scan.perception.ScannerUiState
import dev.wellfriend.scan.perception.NativeLibraryLoader
import dev.wellfriend.scan.perception.NativeRuntimeArtifactDiagnostics
import dev.wellfriend.scan.ui.camera.CameraPreview
import dev.wellfriend.scan.ui.camera.CameraXScannerController
import dev.wellfriend.scan.ui.overlay.CaptureGuidancePanel
import dev.wellfriend.scan.ui.overlay.LiveDocumentOverlay
import dev.wellfriend.scan.ui.overlay.ManualCropEditor
import kotlinx.coroutines.launch

/** Android product shell: it renders state from ScanController and invokes only its public intents. */
@Composable
fun WellfriendScannerApp(
    scanController: ScanController,
    cameraController: CameraXScannerController,
    onRequestCameraPermission: () -> Unit,
    onManualCapture: () -> Unit,
    onGalleryImport: () -> Unit,
    onExportDebug: () -> Unit,
) {
    val state by scanController.state.collectAsState()
    MaterialTheme {
        when (state.state) {
            ScannerState.EDITING_CROP -> CropEditorScreen(state, scanController)
            ScannerState.REVIEWING_PAGE,
            ScannerState.APPLYING_FILTER,
            ScannerState.PAGE_ACCEPTED,
            ScannerState.EXPORTING -> ReviewScreen(state, scanController, onExportDebug)
            else -> CameraScreen(
                state,
                cameraController,
                onRequestCameraPermission,
                onManualCapture,
                onGalleryImport,
            )
        }
    }
}

@Composable
private fun CameraScreen(
    state: ScannerUiState,
    cameraController: CameraXScannerController,
    onRequestCameraPermission: () -> Unit,
    onManualCapture: () -> Unit,
    onGalleryImport: () -> Unit,
) {
    var torchEnabled by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(
            controller = cameraController,
            enabled = state.state !in setOf(ScannerState.IDLE, ScannerState.REQUESTING_PERMISSION, ScannerState.ERROR),
            modifier = Modifier.fillMaxSize(),
        )
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
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CaptureGuidancePanel(
                state.guidance,
                state.analysis?.captureReadinessScore,
                Modifier.align(Alignment.CenterHorizontally),
            )
            state.error?.let { Text(it, color = Color.Red) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = onRequestCameraPermission) { Text("Camera") }
                OutlinedButton(onClick = {
                    torchEnabled = !torchEnabled
                    cameraController.setTorch(torchEnabled)
                }) { Text(if (torchEnabled) "Torch on" else "Torch off") }
                OutlinedButton(onClick = { cameraController.setZoom(0.25f) }) { Text("Zoom") }
                OutlinedButton(onClick = { cameraController.setExposureCompensation(1) }) { Text("Exposure+") }
                OutlinedButton(onClick = { cameraController.switchCamera() }) { Text("Switch") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = onManualCapture) { Text("Capture") }
                OutlinedButton(onClick = onGalleryImport) { Text("Import") }
            }
            DebugDiagnostics(state, cameraController)
        }
    }
}

@Composable
private fun CropEditorScreen(state: ScannerUiState, scanController: ScanController) {
    val page = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    val geometry = page?.effectiveGeometry
    if (page == null || geometry == null) {
        Text("A valid page geometry is required before crop editing.")
        return
    }
    ManualCropEditor(
        geometry = geometry,
        onApply = { corners ->
            scanController.applyManualCrop(page.id, corners)
                .onFailure { /* controller performs no invalid-geometry reconstruction */ }
        },
        onReset = { scanController.resetToDetectedCrop(page.id) },
        onCancel = { scanController.cancelManualCrop(page.id) },
        modifier = Modifier.fillMaxSize().padding(16.dp),
    )
}

@Composable
private fun ReviewScreen(state: ScannerUiState, scanController: ScanController, onExportDebug: () -> Unit) {
    val scope = rememberCoroutineScope()
    val active = state.activePageId?.let { id -> state.session.pages.firstOrNull { it.id == id } }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Session: ${state.session.pages.size} page(s)", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.session.pages, key = { it.id }) { page ->
                PageThumbnail(page, selected = page.id == active?.id, onSelect = { scanController.selectReviewPage(page.id) })
            }
        }
        if (active != null) {
            Text("Page ${active.id.takeLast(6)} · ${active.filter.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { scanController.reconstructPage(active.id) } }) { Text("Reconstruct") }
                OutlinedButton(onClick = { scanController.beginManualCrop(active.id) }) { Text("Crop") }
                OutlinedButton(onClick = { scanController.rotatePage(active.id) }) { Text("Rotate") }
                OutlinedButton(onClick = { scanController.deletePage(active.id) }) { Text("Delete") }
                val index = state.session.pages.indexOfFirst { it.id == active.id }
                OutlinedButton(
                    enabled = index > 0,
                    onClick = { scanController.reorderPages(index, index - 1) },
                ) { Text("Move up") }
                OutlinedButton(
                    enabled = index >= 0 && index < state.session.pages.lastIndex,
                    onClick = { scanController.reorderPages(index, index + 1) },
                ) { Text("Move down") }
            }
            FilterRow(active, scanController)
            Text("Advanced filters are experimental and may remain no-op: Receipt, Book, Whiteboard, Photo document.")
            Text(active.diagnostics.joinToString(separator = "\n"))
        }
        Button(onClick = onExportDebug) { Text("Export debug JSON") }
        DebugDiagnostics(state)
    }
}

@Composable
private fun PageThumbnail(page: ScanPage, selected: Boolean, onSelect: () -> Unit) {
    OutlinedButton(onClick = onSelect) {
        Text(if (selected) "• ${page.id.takeLast(4)}" else page.id.takeLast(4))
    }
}

@Composable
private fun FilterRow(active: ScanPage, scanController: ScanController) {
    val scope = rememberCoroutineScope()
    val presets = listOf(
        FilterPreset.ORIGINAL,
        FilterPreset.AUTO,
        FilterPreset.CLEAN,
        FilterPreset.COLOR,
        FilterPreset.GRAYSCALE,
        FilterPreset.BLACK_AND_WHITE,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(presets) { preset ->
            OutlinedButton(onClick = { scope.launch { scanController.applyFilter(active.id, preset) } }) {
                Text(preset.name.replace('_', ' '))
            }
        }
    }
}

@Composable
private fun DebugDiagnostics(state: ScannerUiState, cameraController: CameraXScannerController? = null) {
    val cameraDiagnostics by cameraController?.diagnostics?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val artifact = NativeRuntimeArtifactDiagnostics.snapshot()
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xAA111111)).padding(8.dp)) {
        val analysis = state.analysis
        Text("Runtime ${analysis?.engineMode ?: "pending"}: ${NativeLibraryLoader.status.diagnostic}", color = Color.White)
        Text("Artifact ${artifact.sourceSha ?: "unavailable"} schema ${artifact.schemaVersion ?: "?"}", color = Color.White)
        cameraDiagnostics?.let { camera ->
            Text("Camera permission=${camera.permissionGranted} provider=${camera.providerObtained} preview=${camera.previewSurfaceAttached} bound=${camera.useCasesBound}", color = Color.White)
            Text("Camera lens=${camera.lensFacing} frames=${camera.frameCount} ${camera.lastFrame ?: "no frame yet"}", color = Color.White)
            camera.lastError?.let { Text("Camera error: $it", color = Color.Red) }
        }
        analysis?.let {
            Text("Frame rotation ${it.rotationDegrees}; mirror ${it.mirrored}", color = Color.White)
            Text("Debug · ${it.engineMode} · ${it.inputSize.width}×${it.inputSize.height}", color = Color.White)
            Text("Readiness ${it.captureReadiness} ${it.captureReadinessScore}", color = Color.White)
            Text("Timings ${it.stageTimingsMillis}", color = Color.White)
            Text(it.diagnostics.joinToString(), color = Color.White)
        }
        artifact.warning?.let { Text("Artifact warning: $it", color = Color.Yellow) }
    }
}
