package dev.wellfriend.scan.testing

import dev.wellfriend.scan.core.CaptureGuidance
import dev.wellfriend.scan.core.CaptureMode
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.GeometrySource
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.core.PageGeometry
import dev.wellfriend.scan.core.Point2D
import dev.wellfriend.scan.core.PreviewCoordinateMapper
import dev.wellfriend.scan.core.PreviewScaleMode
import dev.wellfriend.scan.core.PreviewSize
import dev.wellfriend.scan.core.ScanSession
import dev.wellfriend.scan.export.ExportFormat
import dev.wellfriend.scan.export.ExportRequest
import dev.wellfriend.scan.export.ScanExporter
import dev.wellfriend.scan.perception.CaptureReadiness
import dev.wellfriend.scan.perception.ConditionVectorDto
import dev.wellfriend.scan.perception.DevMockPerceptionEngine
import dev.wellfriend.scan.perception.FrameAnalysisResult
import dev.wellfriend.scan.perception.PerceptionEngine
import dev.wellfriend.scan.perception.PerceptionEngineMode
import dev.wellfriend.scan.perception.PerceptionFrame
import dev.wellfriend.scan.perception.PerceptionPixelFormat
import dev.wellfriend.scan.perception.QualityReportDto
import dev.wellfriend.scan.perception.RefinementResultDto
import dev.wellfriend.scan.perception.FusionResultDto
import dev.wellfriend.scan.perception.TemporalStateDto
import dev.wellfriend.scan.perception.ReconstructionRequest
import dev.wellfriend.scan.perception.ReconstructionResult
import dev.wellfriend.scan.perception.FilterRequest
import dev.wellfriend.scan.perception.FilterResult
import dev.wellfriend.scan.perception.ScanController
import java.nio.file.Files
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ScannerIntegrationTest {
    private fun frame() = PerceptionFrame(
        frameId = 1,
        timestampMillis = 1,
        size = ImageSize(100, 200),
        rotationDegrees = 0,
        pixelFormat = PerceptionPixelFormat.GRAY8,
        rowStrideBytes = 100,
        bytes = ByteArray(100) { 1 },
        source = "synthetic",
    )

    @Test fun `mock perception drives capture crop reconstruction filter and debug export`() = runBlocking {
        val engine = RecordingEngine()
        val controller = ScanController(engine, ScanSession("synthetic-session"))
        controller.requestPermission()
        controller.onPermissionResult(true)
        controller.onCameraStarted()
        val analysis = controller.analyzeFrame(frame())
        assertEquals(CaptureReadiness.CAPTURE_NOW, analysis.captureReadiness)
        assertTrue(controller.requestCapture(CaptureMode.AUTO))
        val page = controller.onPhotoCaptured("file:///synthetic.jpg", ImageSize(1000, 2000))
        assertEquals(GeometrySource.AUTO, page.detectedGeometry?.source)
        assertTrue(controller.applyManualCrop(page.id, listOf(
            Point2D(10f, 10f), Point2D(990f, 10f), Point2D(990f, 1990f), Point2D(10f, 1990f),
        )).isSuccess)
        controller.reconstructPage(page.id)
        controller.applyFilter(page.id, FilterPreset.GRAYSCALE)
        val output = Files.createTempDirectory("wellfriend-scan-test").toFile()
        val export = ScanExporter.exportDebugJson(ExportRequest(controller.state.value.session, ExportFormat.JSON_DEBUG, output))
        assertTrue(export.outputFiles.single().readText().contains("synthetic-session"))
        assertEquals(GeometrySource.MANUAL, controller.state.value.session.pages.single().effectiveGeometry?.source)
        assertEquals(GeometrySource.MANUAL, engine.reconstructionRequest?.geometry?.source)
    }

    @Test fun `auto capture refuses non ready perception while manual remains intentional`() = runBlocking {
        val controller = ScanController(NoDocumentEngine())
        controller.analyzeFrame(frame())
        assertEquals(CaptureGuidance.NO_DOCUMENT, controller.state.value.guidance.single())
        assertFalse(controller.requestCapture(CaptureMode.AUTO))
        assertTrue(controller.requestCapture(CaptureMode.MANUAL))
        assertEquals(CaptureGuidance.CAPTURING, controller.state.value.guidance.single())
    }

    @Test fun `coordinate mapper round trips rotation and letterboxing`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val mapper = PreviewCoordinateMapper(
                ImageSize(400, 200), PreviewSize(300f, 300f), rotation, PreviewScaleMode.FIT, mirrored = false,
            )
            val source = Point2D(80f, 50f)
            val recovered = mapper.previewToImage(mapper.imageToPreview(source))
            assertTrue(abs(source.x - recovered.x) < 0.001f)
            assertTrue(abs(source.y - recovered.y) < 0.001f)
        }
        val mirrored = PreviewCoordinateMapper(
            ImageSize(400, 200), PreviewSize(300f, 300f), 0, PreviewScaleMode.FILL, mirrored = true,
        )
        assertTrue(mirrored.imageToPreview(Point2D(0f, 0f)).x > mirrored.imageToPreview(Point2D(400f, 0f)).x)
    }

    @Test fun `hold steady evidence keeps auto capture unavailable`() = runBlocking {
        val controller = ScanController(HoldSteadyEngine())
        controller.analyzeFrame(frame())
        assertEquals(CaptureGuidance.HOLD_STEADY, controller.state.value.guidance.single())
        assertFalse(controller.requestCapture(CaptureMode.AUTO))
    }
}

private class RecordingEngine : PerceptionEngine {
    private val delegate = DevMockPerceptionEngine()
    var reconstructionRequest: ReconstructionRequest? = null

    override val mode = delegate.mode
    override suspend fun analyzeFrame(frame: PerceptionFrame) = delegate.analyzeFrame(frame)
    override suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult {
        reconstructionRequest = request
        return delegate.reconstructPage(request)
    }
    override suspend fun applyFilter(request: FilterRequest): FilterResult = delegate.applyFilter(request)
}

private class NoDocumentEngine : PerceptionEngine {
    override val mode = PerceptionEngineMode.DEV_JVM_MOCK
    override suspend fun analyzeFrame(frame: PerceptionFrame) = FrameAnalysisResult(
        inputSize = frame.size,
        rotationDegrees = frame.rotationDegrees,
        mirrored = false,
        qualityReport = QualityReportDto(emptyMap(), emptyList(), 0f),
        conditionVector = ConditionVectorDto(emptyMap()),
        candidates = emptyList(),
        fusionResult = FusionResultDto(null, 0f, emptyList(), emptyList(), 1f),
        refinementResult = RefinementResultDto(null, 0f, emptyList()),
        temporalState = TemporalStateDto(0f, false, 1),
        captureReadiness = CaptureReadiness.NOT_READY,
        captureReadinessScore = 0f,
        guidance = listOf(CaptureGuidance.NO_DOCUMENT),
        diagnostics = listOf("test no document"),
        stageTimingsMillis = emptyMap(),
        engineMode = mode,
    )
    override suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult = error("not expected")
    override suspend fun applyFilter(request: FilterRequest): FilterResult = error("not expected")
}

private class HoldSteadyEngine : PerceptionEngine {
    private val delegate = NoDocumentEngine()
    override val mode = delegate.mode
    override suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult = delegate.analyzeFrame(frame).copy(
        captureReadiness = CaptureReadiness.ALMOST_READY,
        guidance = listOf(CaptureGuidance.HOLD_STEADY),
    )
    override suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult = error("not expected")
    override suspend fun applyFilter(request: FilterRequest): FilterResult = error("not expected")
}
