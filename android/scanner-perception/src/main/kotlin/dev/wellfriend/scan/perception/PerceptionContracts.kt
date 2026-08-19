package dev.wellfriend.scan.perception

import dev.wellfriend.scan.core.CaptureGuidance
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.GeometrySource
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.core.PageGeometry
import dev.wellfriend.scan.core.Point2D

/** Wire-compatible names for the MP3/MP4 perception evidence graph. */
enum class PerceptionEngineMode { NATIVE, DEV_JVM_MOCK }
enum class PerceptionPixelFormat { GRAY8, RGB8, BGR8, RGBA8, YUV420 }
enum class CaptureReadiness { NOT_READY, ALMOST_READY, READY, CAPTURE_NOW }

data class PerceptionFrame(
    val frameId: Long,
    val timestampMillis: Long,
    val size: ImageSize,
    val rotationDegrees: Int,
    val pixelFormat: PerceptionPixelFormat,
    val rowStrideBytes: Int,
    val bytes: ByteArray,
    val source: String,
    val mirrored: Boolean = false,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "frame rotation is invalid" }
        require(rowStrideBytes >= size.width) { "frame row stride is smaller than image width" }
        require(bytes.isNotEmpty()) { "perception frame bytes must not be empty" }
    }
}

data class QualityMetricDto(val rawValue: Float, val normalizedScore: Float, val confidence: Float)
data class QualityReportDto(
    val metrics: Map<String, QualityMetricDto>,
    val warnings: List<String>,
    val confidence: Float,
)
data class ConditionEvidenceDto(
    val score: Float,
    val confidence: Float,
    val source: String,
    val recommendedProcessorIds: List<String>,
)
data class ConditionVectorDto(val conditions: Map<String, ConditionEvidenceDto>)

data class DetectionCandidateDto(
    val geometry: PageGeometry,
    val score: Float,
    val confidence: Float,
    val source: String,
    val diagnostics: List<String> = emptyList(),
)

data class FusionResultDto(
    val geometry: PageGeometry?,
    val confidence: Float,
    val contributingSources: List<String>,
    val rejectedSources: List<String>,
    val disagreementScore: Float,
)

data class RefinementResultDto(val geometry: PageGeometry?, val confidence: Float, val diagnostics: List<String>)
data class TemporalStateDto(val stability: Float, val stable: Boolean, val frameCount: Int)

data class FrameAnalysisResult(
    val inputSize: ImageSize,
    val rotationDegrees: Int,
    val mirrored: Boolean,
    val qualityReport: QualityReportDto,
    val conditionVector: ConditionVectorDto,
    val candidates: List<DetectionCandidateDto>,
    val fusionResult: FusionResultDto,
    val refinementResult: RefinementResultDto,
    val temporalState: TemporalStateDto,
    val captureReadiness: CaptureReadiness,
    val captureReadinessScore: Float,
    val guidance: List<CaptureGuidance>,
    val diagnostics: List<String>,
    val stageTimingsMillis: Map<String, Long>,
    val engineMode: PerceptionEngineMode,
) {
    val overlayGeometry: PageGeometry? get() = refinementResult.geometry ?: fusionResult.geometry
}

data class ReconstructionRequest(
    val pageId: String,
    val sourceUri: String,
    val sourceSize: ImageSize,
    val geometry: PageGeometry,
    val outputLongEdge: Int = 2048,
    val aspectPolicy: String = "free_from_quad",
    val orientationPolicy: String = "preserve_source",
    val cropMarginPolicy: String = "safe_inner",
) {
    init {
        require(outputLongEdge in 256..4096) { "output size is outside the safe scanner range" }
    }
}

data class ReconstructionResult(
    val outputUri: String,
    val outputSize: ImageSize,
    val diagnostics: List<String>,
    val confidence: Float,
    val engineMode: PerceptionEngineMode,
)

data class FilterRequest(
    val pageId: String,
    val inputUri: String,
    val preset: FilterPreset,
    val conditionVector: ConditionVectorDto,
)

data class FilterResult(
    val outputUri: String,
    val appliedProcessorIds: List<String>,
    val diagnostics: List<String>,
    val engineMode: PerceptionEngineMode,
)

/** The only scanner-to-core algorithm entry point. Kotlin UI must not add detector logic. */
interface PerceptionEngine {
    val mode: PerceptionEngineMode
    suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult
    suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult
    suspend fun applyFilter(request: FilterRequest): FilterResult
}

/** Generated JNI/C ABI implementations will satisfy this bridge once the perception Android ABI ships. */
interface NativePerceptionBridge {
    suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult
    suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult
    suspend fun applyFilter(request: FilterRequest): FilterResult
}

class NativePerceptionEngine(private val bridge: NativePerceptionBridge) : PerceptionEngine {
    override val mode = PerceptionEngineMode.NATIVE
    override suspend fun analyzeFrame(frame: PerceptionFrame) = bridge.analyzeFrame(frame)
    override suspend fun reconstructPage(request: ReconstructionRequest) = bridge.reconstructPage(request)
    override suspend fun applyFilter(request: FilterRequest) = bridge.applyFilter(request)
}

class NativePerceptionUnavailableException(message: String) : IllegalStateException(message)

/**
 * Explicit Option-B seam: there is currently no published wellfriend-perception Android ABI.
 * It fails closed in release instead of falling back to a separate Kotlin detector.
 */
class UnavailableNativePerceptionBridge : NativePerceptionBridge {
    private fun unavailable(): Nothing = throw NativePerceptionUnavailableException(
        "wellfriend-perception Android JNI/C ABI is not published; native analysis is unavailable",
    )

    override suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult = unavailable()
    override suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult = unavailable()
    override suspend fun applyFilter(request: FilterRequest): FilterResult = unavailable()
}

/** Test/dev-only deterministic adapter. It proves product flow; it is not a perception implementation. */
class DevMockPerceptionEngine : PerceptionEngine {
    override val mode = PerceptionEngineMode.DEV_JVM_MOCK

    override suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult {
        val insetX = frame.size.width * 0.1f
        val insetY = frame.size.height * 0.1f
        val geometry = PageGeometry(
            corners = listOf(
                Point2D(insetX, insetY),
                Point2D(frame.size.width - insetX, insetY),
                Point2D(frame.size.width - insetX, frame.size.height - insetY),
                Point2D(insetX, frame.size.height - insetY),
            ),
            imageSize = frame.size,
            confidence = 0.9f,
            source = GeometrySource.AUTO,
        )
        val quality = QualityReportDto(
            metrics = mapOf("mock_quality" to QualityMetricDto(1f, 1f, 0f)),
            warnings = emptyList(),
            confidence = 0f,
        )
        return FrameAnalysisResult(
            inputSize = frame.size,
            rotationDegrees = frame.rotationDegrees,
            mirrored = frame.mirrored,
            qualityReport = quality,
            conditionVector = ConditionVectorDto(emptyMap()),
            candidates = listOf(DetectionCandidateDto(geometry, 0.9f, 0.9f, "dev_mock")),
            fusionResult = FusionResultDto(geometry, 0.9f, listOf("dev_mock"), emptyList(), 0f),
            refinementResult = RefinementResultDto(geometry, 0.9f, listOf("dev_only_mock")),
            temporalState = TemporalStateDto(0.95f, true, 1),
            captureReadiness = CaptureReadiness.CAPTURE_NOW,
            captureReadinessScore = 0.95f,
            guidance = listOf(CaptureGuidance.READY),
            diagnostics = listOf("dev_only_mock_perception; not for production capture quality"),
            stageTimingsMillis = mapOf("mock" to 0L),
            engineMode = mode,
        )
    }

    override suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult = ReconstructionResult(
        outputUri = request.sourceUri,
        outputSize = request.sourceSize,
        diagnostics = listOf("dev_only_mock_reconstruction; no pixel transform was performed"),
        confidence = request.geometry.confidence,
        engineMode = mode,
    )

    override suspend fun applyFilter(request: FilterRequest): FilterResult = FilterResult(
        outputUri = request.inputUri,
        appliedProcessorIds = emptyList(),
        diagnostics = listOf("dev_only_mock_filter; no pixel transform was performed"),
        engineMode = mode,
    )
}

object PerceptionEngineFactory {
    fun create(isDebugBuild: Boolean): PerceptionEngine = if (isDebugBuild) {
        JniNativePerceptionBridge.createOrNull()?.let(::NativePerceptionEngine) ?: DevMockPerceptionEngine()
    } else {
        JniNativePerceptionBridge.createOrNull()?.let(::NativePerceptionEngine)
            ?: NativePerceptionEngine(UnavailableNativePerceptionBridge())
    }
}
