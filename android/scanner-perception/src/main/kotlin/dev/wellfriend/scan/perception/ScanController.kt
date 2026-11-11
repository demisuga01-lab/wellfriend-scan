package dev.wellfriend.scan.perception

import dev.wellfriend.scan.core.CanonicalPagePreview
import dev.wellfriend.scan.core.CaptureGuidance
import dev.wellfriend.scan.core.CaptureMode
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.GeometrySource
import dev.wellfriend.scan.core.GeometryMapper
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.core.PageGeometry
import dev.wellfriend.scan.core.PageState
import dev.wellfriend.scan.core.Point2D
import dev.wellfriend.scan.core.ProcessingStatus
import dev.wellfriend.scan.core.ScanPage
import dev.wellfriend.scan.core.ScanSession
import dev.wellfriend.scan.core.ScanSessionReducer
import dev.wellfriend.scan.core.ScannerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Compose-independent scanner state machine driven by perception evidence, never UI heuristics. */
data class ScannerUiState(
    val state: ScannerState = ScannerState.IDLE,
    val session: ScanSession,
    val activePageId: String? = null,
    val analysis: FrameAnalysisResult? = null,
    val guidance: List<CaptureGuidance> = emptyList(),
    val error: String? = null,
)

class ScanController(
    private val perceptionEngine: PerceptionEngine,
    initialSession: ScanSession = ScanSession(id = "session-${System.currentTimeMillis()}"),
) {
    @Volatile private var autoCaptureArmed = false
    private val mutableState = MutableStateFlow(ScannerUiState(session = initialSession))
    val state: StateFlow<ScannerUiState> = mutableState.asStateFlow()

    val engineMode: PerceptionEngineMode get() = perceptionEngine.mode

    fun requestPermission() = transition(ScannerState.REQUESTING_PERMISSION)

    fun onPermissionResult(granted: Boolean) {
        if (granted) transition(ScannerState.CAMERA_STARTING) else fail("camera permission denied")
    }

    fun onCameraStarted() {
        if (mutableState.value.state in setOf(ScannerState.CAMERA_STARTING, ScannerState.SEARCHING_FOR_DOCUMENT)) {
            transition(ScannerState.SEARCHING_FOR_DOCUMENT)
        }
    }

    fun setAutoCaptureArmed(armed: Boolean) { autoCaptureArmed = armed }
    fun isAutoCaptureArmed(): Boolean = autoCaptureArmed

    fun onCameraFailure(message: String) = fail(message)
    fun onGalleryImportFailure(message: String) = fail(message)

    /** Call from a background coroutine after ImageProxy has been copied and closed. */
    suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult {
        return try {
            val result = perceptionEngine.analyzeFrame(frame)
            // CameraX can deliver one more frame while ImageCapture is writing. Preserve the
            // capture transaction instead of letting a late analysis move the product back to READY.
            if (mutableState.value.state == ScannerState.CAPTURING) return result
            val next = when (result.captureReadiness) {
                CaptureReadiness.NOT_READY -> if (result.overlayGeometry == null) {
                    ScannerState.SEARCHING_FOR_DOCUMENT
                } else {
                    ScannerState.DOCUMENT_CANDIDATE_FOUND
                }
                CaptureReadiness.ALMOST_READY -> ScannerState.ALMOST_READY
                CaptureReadiness.READY, CaptureReadiness.CAPTURE_NOW -> ScannerState.READY
            }
            mutableState.value = mutableState.value.copy(
                state = next,
                analysis = result,
                guidance = result.guidance,
                error = null,
            )
            result
        } catch (error: Exception) {
            fail(error.message ?: "perception analysis failed")
            throw error
        }
    }

    /** Auto mode only proceeds when the perception engine returns CaptureNow. */
    fun requestCapture(mode: CaptureMode): Boolean {
        val analysis = mutableState.value.analysis
        if (mutableState.value.state == ScannerState.CAPTURING) return false
        if (mode == CaptureMode.AUTO && analysis?.captureReadiness != CaptureReadiness.CAPTURE_NOW) return false
        mutableState.value = mutableState.value.copy(
            state = ScannerState.CAPTURING,
            guidance = listOf(CaptureGuidance.CAPTURING),
        )
        return true
    }

    /** Registers a CameraX high-resolution capture without storing image bytes in session memory. */
    fun onPhotoCaptured(sourceUri: String, sourceSize: ImageSize, thumbnailUri: String? = null): ScanPage {
        val detected = mutableState.value.analysis?.overlayGeometry?.let {
            GeometryMapper.mapToImageSize(it, sourceSize)
        }
        val page = ScanPage(
            id = "page-${System.nanoTime()}",
            sourceUri = sourceUri,
            sourceSize = sourceSize,
            thumbnailUri = thumbnailUri,
            detectedGeometry = detected,
            state = PageState.READY,
            processingStatus = ProcessingStatus.IDLE,
            diagnostics = mutableState.value.analysis?.diagnostics.orEmpty(),
        )
        val current = mutableState.value
        mutableState.value = current.copy(
            state = ScannerState.REVIEWING_PAGE,
            session = ScanSessionReducer.addPage(current.session, page),
            activePageId = page.id,
            guidance = emptyList(),
        )
        return page
    }

    /** Gallery import has already passed host decoder bounds checks and follows the same perception route. */
    suspend fun importGallery(sourceUri: String, sourceSize: ImageSize, analysisFrame: PerceptionFrame): ScanPage {
        analyzeFrame(analysisFrame)
        return onPhotoCaptured(sourceUri, sourceSize)
    }

    fun beginManualCrop(pageId: String) {
        val current = mutableState.value
        val page = ScanSessionReducer.page(current.session, pageId)
        // This is a manual full-image starting frame, not a detector fallback. The user must still
        // adjust or accept it, and PageGeometry validates it before reconstruction.
        val initialized = if (page.effectiveGeometry == null) page.copy(
            manualGeometry = fullImageManualGeometry(page.sourceSize),
            diagnostics = page.diagnostics + "manual_crop_initialized_to_source_bounds",
        ) else page
        mutableState.value = current.copy(
            state = ScannerState.EDITING_CROP,
            activePageId = pageId,
            session = ScanSessionReducer.updatePage(current.session, initialized),
        )
    }

    /** Manual geometry is validated and labeled before it can enter reconstruction. */
    fun applyManualCrop(pageId: String, corners: List<Point2D>): Result<Unit> = runCatching {
        val current = mutableState.value
        val page = ScanSessionReducer.page(current.session, pageId)
        val geometry = PageGeometry(corners, page.sourceSize, 1f, GeometrySource.MANUAL)
        val changed = page.copy(
            manualGeometry = geometry,
            state = PageState.RECONSTRUCTING,
            processingStatus = ProcessingStatus.QUEUED,
            diagnostics = page.diagnostics + "manual_geometry_validated_for_perception_fusion",
        )
        mutableState.value = current.copy(
            state = ScannerState.REVIEWING_PAGE,
            session = ScanSessionReducer.updatePage(current.session, changed),
            activePageId = pageId,
        )
    }

    fun resetToDetectedCrop(pageId: String) {
        val current = mutableState.value
        val page = ScanSessionReducer.page(current.session, pageId)
        val changed = if (page.detectedGeometry != null) {
            page.copy(manualGeometry = null, diagnostics = page.diagnostics + "manual_geometry_reset_to_detected")
        } else {
            page.copy(manualGeometry = fullImageManualGeometry(page.sourceSize), diagnostics = page.diagnostics + "manual_geometry_reset_to_source_bounds")
        }
        mutableState.value = current.copy(session = ScanSessionReducer.updatePage(current.session, changed))
    }

    fun cancelManualCrop(pageId: String) {
        ensurePage(pageId)
        mutableState.value = mutableState.value.copy(state = ScannerState.REVIEWING_PAGE, activePageId = pageId)
    }

    fun selectReviewPage(pageId: String) {
        ensurePage(pageId)
        mutableState.value = mutableState.value.copy(state = ScannerState.REVIEWING_PAGE, activePageId = pageId)
    }

    suspend fun reconstructPage(pageId: String): ReconstructionResult {
        val current = mutableState.value
        val page = ScanSessionReducer.page(current.session, pageId)
        val geometry = requireNotNull(page.effectiveGeometry) { "a valid detected or manual page geometry is required" }
        val queued = page.copy(state = PageState.RECONSTRUCTING, processingStatus = ProcessingStatus.RUNNING)
        mutableState.value = current.copy(
            state = ScannerState.APPLYING_FILTER,
            session = ScanSessionReducer.updatePage(current.session, queued),
            activePageId = pageId,
        )
        return try {
            val result = perceptionEngine.reconstructPage(
                ReconstructionRequest(pageId, page.sourceUri, page.sourceSize, geometry),
            )
            val latest = mutableState.value
            val finished = ScanSessionReducer.page(latest.session, pageId).copy(
                canonicalPage = CanonicalPagePreview(result.outputUri, result.outputSize, result.diagnostics),
                state = PageState.READY,
                processingStatus = ProcessingStatus.COMPLETE,
                diagnostics = page.diagnostics + result.diagnostics,
            )
            mutableState.value = latest.copy(
                state = ScannerState.REVIEWING_PAGE,
                session = ScanSessionReducer.updatePage(latest.session, finished),
            )
            result
        } catch (error: Exception) {
            fail(error.message ?: "reconstruction failed")
            throw error
        }
    }

    suspend fun applyFilter(pageId: String, preset: FilterPreset): FilterResult {
        val current = mutableState.value
        val page = ScanSessionReducer.page(current.session, pageId)
        val inputUri = page.canonicalPage?.uri ?: page.sourceUri
        mutableState.value = current.copy(state = ScannerState.APPLYING_FILTER, activePageId = pageId)
        return try {
            val result = perceptionEngine.applyFilter(
                FilterRequest(
                    pageId,
                    inputUri,
                    preset,
                    current.analysis?.conditionVector ?: ConditionVectorDto(emptyMap()),
                ),
            )
            val latest = mutableState.value
            val changed = ScanSessionReducer.page(latest.session, pageId).copy(
                filter = preset,
                filteredPage = result.outputSize?.let { CanonicalPagePreview(result.outputUri, it, result.diagnostics) },
                processingStatus = ProcessingStatus.COMPLETE,
                diagnostics = page.diagnostics + result.diagnostics,
            )
            mutableState.value = latest.copy(
                state = ScannerState.REVIEWING_PAGE,
                session = ScanSessionReducer.updatePage(latest.session, changed),
            )
            result
        } catch (error: Exception) {
            fail(error.message ?: "filter application failed")
            throw error
        }
    }

    fun rotatePage(pageId: String) = updateSession { ScanSessionReducer.rotatePage(it, pageId) }
    fun continueScanning() = transition(ScannerState.SEARCHING_FOR_DOCUMENT)
    fun retakePage(pageId: String) {
        deletePage(pageId)
        continueScanning()
    }

    suspend fun applyFilterToAll(preset: FilterPreset) {
        val pageIds = mutableState.value.session.pages.map { it.id }
        pageIds.forEach { pageId ->
            if (mutableState.value.session.pages.any { it.id == pageId }) applyFilter(pageId, preset)
        }
    }
    fun deletePage(pageId: String) {
        val current = mutableState.value
        val session = ScanSessionReducer.deletePage(current.session, pageId)
        mutableState.value = current.copy(
            session = session,
            activePageId = session.pages.firstOrNull()?.id,
            state = if (session.pages.isEmpty()) ScannerState.SEARCHING_FOR_DOCUMENT else ScannerState.REVIEWING_PAGE,
        )
    }
    fun reorderPages(fromIndex: Int, toIndex: Int) = updateSession { ScanSessionReducer.reorderPage(it, fromIndex, toIndex) }

    fun acceptPage(pageId: String) {
        ensurePage(pageId)
        mutableState.value = mutableState.value.copy(state = ScannerState.PAGE_ACCEPTED, activePageId = pageId)
    }

    fun beginExport() = transition(ScannerState.EXPORTING)

    private fun transition(state: ScannerState) {
        mutableState.value = mutableState.value.copy(state = state, error = null)
    }

    private fun updateSession(transform: (ScanSession) -> ScanSession) {
        val current = mutableState.value
        mutableState.value = current.copy(session = transform(current.session))
    }

    private fun ensurePage(pageId: String) {
        ScanSessionReducer.page(mutableState.value.session, pageId)
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(state = ScannerState.ERROR, error = message)
    }

    private fun fullImageManualGeometry(size: ImageSize): PageGeometry = PageGeometry(
        corners = listOf(
            Point2D(0f, 0f),
            Point2D(size.width.toFloat(), 0f),
            Point2D(size.width.toFloat(), size.height.toFloat()),
            Point2D(0f, size.height.toFloat()),
        ),
        imageSize = size,
        confidence = 1f,
        source = GeometrySource.MANUAL,
    )
}
