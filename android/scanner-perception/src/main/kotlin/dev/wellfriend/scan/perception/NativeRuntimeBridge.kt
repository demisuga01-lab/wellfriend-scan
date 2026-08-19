package dev.wellfriend.scan.perception

import dev.wellfriend.scan.core.CaptureGuidance
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.GeometrySource
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.core.PageGeometry
import dev.wellfriend.scan.core.Point2D

/** Runtime library state is visible to diagnostics; release never falls back to a Kotlin algorithm. */
object NativeLibraryLoader {
    data class Status(val available: Boolean, val diagnostic: String)

    val status: Status by lazy {
        runCatching { System.loadLibrary("wellfriend_perception_jni") }
            .fold(
                onSuccess = { Status(true, "wellfriend_perception_jni loaded") },
                onFailure = { Status(false, "wellfriend_perception_jni unavailable: ${it.message}") },
            )
    }
}

/**
 * JNI implementation that delegates all detection, reconstruction, and filtering to the Rust C ABI.
 * The tiny C shim is intentionally the only Android-native code allowed to call `wf_*` functions.
 */
class JniNativePerceptionBridge private constructor(private val engineHandle: Long) : NativePerceptionBridge, AutoCloseable {
    companion object {
        fun createOrNull(): JniNativePerceptionBridge? {
            if (!NativeLibraryLoader.status.available) return null
            val handle = nativeCreate("{}")
            return handle.takeIf { it != 0L }?.let(::JniNativePerceptionBridge)
        }

        @JvmStatic private external fun nativeCreate(configJson: String): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeAnalyze(
            handle: Long, bytes: ByteArray, width: Int, height: Int, stride: Int, pixelFormat: String, requestJson: String,
        ): String
        @JvmStatic private external fun nativeReconstruct(
            handle: Long, bytes: ByteArray, width: Int, height: Int, stride: Int, pixelFormat: String, requestJson: String,
        ): String
        @JvmStatic private external fun nativeApplyFilter(
            handle: Long, bytes: ByteArray, width: Int, height: Int, stride: Int, pixelFormat: String, requestJson: String,
        ): String
    }

    override suspend fun analyzeFrame(frame: PerceptionFrame): FrameAnalysisResult {
        val raw = nativeAnalyze(
            engineHandle, frame.bytes, frame.size.width, frame.size.height, frame.rowStrideBytes,
            frame.pixelFormat.runtimeName(), "{\"frame_index\":${frame.frameId}}",
        )
        return NativeJsonMapper.analyze(raw, frame)
    }

    override suspend fun reconstructPage(request: ReconstructionRequest): ReconstructionResult {
        throw NativePerceptionUnavailableException(
            "native reconstruction needs decoded source pixels from the gallery/camera store; request wiring is present but no source-image provider was supplied",
        )
    }

    override suspend fun applyFilter(request: FilterRequest): FilterResult {
        throw NativePerceptionUnavailableException(
            "native filtering needs decoded canonical pixels from the page store; request wiring is present but no source-image provider was supplied",
        )
    }

    override fun close() = nativeDestroy(engineHandle)
}

/** Strict, dependency-free mapper for the bounded MP10 runtime JSON schema. */
object NativeJsonMapper {
    fun analyze(json: String, frame: PerceptionFrame): FrameAnalysisResult {
        if (json.contains("\"error\"")) throw NativePerceptionUnavailableException(string(json, "message") ?: "native perception returned an error")
        val confidence = number(json, "capture_readiness_score") ?: 0f
        val geometry = quad(json, "refined_quad", frame.size, confidence) ?: quad(json, "fused_quad", frame.size, confidence)
        val guidance = strings(json, "guidance").mapNotNull { runCatching { CaptureGuidance.valueOf(it) }.getOrNull() }
        val readiness = string(json, "capture_readiness")?.let { runCatching { CaptureReadiness.valueOf(it) }.getOrNull() }
            ?: CaptureReadiness.NOT_READY
        val diagnostics = strings(json, "diagnostics") + "runtime_json_mapped_by_android_bridge"
        return FrameAnalysisResult(
            inputSize = frame.size,
            rotationDegrees = frame.rotationDegrees,
            mirrored = frame.mirrored,
            qualityReport = QualityReportDto(emptyMap(), emptyList(), 0.7f),
            conditionVector = ConditionVectorDto(emptyMap()),
            candidates = geometry?.let { listOf(DetectionCandidateDto(it, confidence, confidence, "native_scalar", diagnostics)) }.orEmpty(),
            fusionResult = FusionResultDto(geometry, confidence, listOf("native_scalar"), emptyList(), 0f),
            refinementResult = RefinementResultDto(geometry, confidence, diagnostics),
            temporalState = TemporalStateDto(0f, false, 1),
            captureReadiness = readiness,
            captureReadinessScore = confidence,
            guidance = guidance,
            diagnostics = diagnostics,
            stageTimingsMillis = emptyMap(),
            engineMode = PerceptionEngineMode.NATIVE,
        )
    }

    private fun string(json: String, key: String): String? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(json)?.groupValues?.get(1)

    private fun number(json: String, key: String): Float? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").find(json)?.groupValues?.get(1)?.toFloatOrNull()

    private fun strings(json: String, key: String): List<String> {
        val match = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\[([^]]*)]", RegexOption.DOT_MATCHES_ALL).find(json) ?: return emptyList()
        return Regex("\\\"([^\\\"]+)\\\"").findAll(match.groupValues[1]).map { it.groupValues[1] }.toList()
    }

    private fun quad(json: String, key: String, size: ImageSize, confidence: Float): PageGeometry? {
        val match = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\{\\s*\\\"points\\\"\\s*:\\s*\\[(.*?)]\\s*}", RegexOption.DOT_MATCHES_ALL).find(json) ?: return null
        val points = Regex("\\{\\s*\\\"x\\\"\\s*:\\s*(-?[0-9.]+)\\s*,\\s*\\\"y\\\"\\s*:\\s*(-?[0-9.]+)\\s*}")
            .findAll(match.groupValues[1])
            .mapNotNull { result -> result.groupValues[1].toFloatOrNull()?.let { x -> result.groupValues[2].toFloatOrNull()?.let { y -> Point2D(x, y) } } }
            .toList()
        return PageGeometry.validate(points, size, confidence.coerceIn(0f, 1f), GeometrySource.AUTO).getOrNull()
    }
}

private fun PerceptionPixelFormat.runtimeName(): String = when (this) {
    PerceptionPixelFormat.GRAY8 -> "Gray8"
    PerceptionPixelFormat.RGB8 -> "Rgb8"
    PerceptionPixelFormat.BGR8 -> "Bgr8"
    PerceptionPixelFormat.RGBA8 -> "Rgba8"
    PerceptionPixelFormat.YUV420 -> throw NativePerceptionUnavailableException("MP10 native scalar ABI accepts converted Gray8/Rgb8/Bgr8/Rgba8 frames, not YUV420 planes")
}
