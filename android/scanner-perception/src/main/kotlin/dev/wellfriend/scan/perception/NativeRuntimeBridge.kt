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
class JniNativePerceptionBridge private constructor(
    private val engineHandle: Long,
    private val runtimeImages: NativeRuntimeImageStore?,
) : NativePerceptionBridge, AutoCloseable {
    companion object {
        fun createOrNull(runtimeImages: NativeRuntimeImageStore? = null): JniNativePerceptionBridge? {
            if (!NativeLibraryLoader.status.available) return null
            val handle = nativeCreate("{}")
            return handle.takeIf { it != 0L }?.let { JniNativePerceptionBridge(it, runtimeImages) }
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
        val input = images().resolve(request.sourceUri, request.sourceSize)
        val raw = nativeReconstruct(
            engineHandle, input.bytes, input.width, input.height, input.stride, input.pixelFormat.runtimeName(),
            "{\"quad\":{\"points\":[${request.geometry.corners.joinToString { "{\"x\":${it.x},\"y\":${it.y}}" }}]},\"output_long_edge\":${request.outputLongEdge},\"aspect_policy\":\"${request.aspectPolicy}\",\"orientation_policy\":\"${request.orientationPolicy}\",\"crop_margin_policy\":\"${request.cropMarginPolicy}\"}",
        )
        return NativeJsonMapper.reconstruction(raw, images())
    }

    override suspend fun applyFilter(request: FilterRequest): FilterResult {
        val input = images().resolve(request.inputUri)
        val raw = nativeApplyFilter(
            engineHandle, input.bytes, input.width, input.height, input.stride, input.pixelFormat.runtimeName(),
            "{\"preset\":\"${request.preset.runtimeName()}\"}",
        )
        return NativeJsonMapper.filter(raw, images())
    }

    override fun close() = nativeDestroy(engineHandle)

    private fun images(): NativeRuntimeImageStore = runtimeImages
        ?: throw NativePerceptionUnavailableException("native reconstruction/filter requires an explicitly registered decoded image store")
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

    fun reconstruction(json: String, images: NativeRuntimeImageStore): ReconstructionResult {
        rejectError(json)
        val image = runtimeImage(json)
        return ReconstructionResult(
            outputUri = images.registerOutput(image),
            outputSize = image.size(),
            diagnostics = strings(json, "diagnostics") + "native_scalar_reconstruction",
            confidence = number(json, "confidence") ?: 0f,
            engineMode = PerceptionEngineMode.NATIVE,
        )
    }

    fun filter(json: String, images: NativeRuntimeImageStore): FilterResult {
        rejectError(json)
        val image = runtimeImage(json)
        return FilterResult(
            outputUri = images.registerOutput(image),
            appliedProcessorIds = strings(json, "applied_processor_ids"),
            diagnostics = strings(json, "diagnostics") + "native_scalar_filter",
            engineMode = PerceptionEngineMode.NATIVE,
        )
    }

    private fun rejectError(json: String) {
        if (json.contains("\"error\"")) throw NativePerceptionUnavailableException(string(json, "message") ?: "native perception returned an error")
    }

    private fun runtimeImage(json: String): NativeRuntimeImage {
        val imageJson = Regex("\\\"image\\\"\\s*:\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL).find(json)?.groupValues?.get(1)
            ?: throw NativePerceptionUnavailableException("native runtime image payload is missing")
        val width = number(imageJson, "width")?.toInt() ?: throw NativePerceptionUnavailableException("runtime image width is missing")
        val height = number(imageJson, "height")?.toInt() ?: throw NativePerceptionUnavailableException("runtime image height is missing")
        val stride = number(imageJson, "stride")?.toInt() ?: throw NativePerceptionUnavailableException("runtime image stride is missing")
        val pixelFormat = string(imageJson, "pixel_format")?.let { runCatching { PerceptionPixelFormat.valueOf(it.uppercase()) }.getOrNull() }
            ?: throw NativePerceptionUnavailableException("runtime image pixel format is invalid")
        val bytesText = Regex("\\\"bytes\\\"\\s*:\\s*\\[([^]]*)]", RegexOption.DOT_MATCHES_ALL).find(imageJson)?.groupValues?.get(1)
            ?: throw NativePerceptionUnavailableException("runtime image bytes are missing")
        if (bytesText.length > MAX_NATIVE_RUNTIME_IMAGE_BYTES * 4) {
            throw NativePerceptionUnavailableException("runtime image JSON exceeds mobile cache limit")
        }
        val byteValues = if (bytesText.isBlank()) emptyList() else bytesText.split(',').map { token ->
            token.trim().toIntOrNull()?.takeIf { it in 0..255 }
                ?: throw NativePerceptionUnavailableException("runtime image byte is invalid")
        }
        return NativeRuntimeImage(width, height, stride, pixelFormat, ByteArray(byteValues.size) { byteValues[it].toByte() })
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

internal fun FilterPreset.runtimeName(): String = when (this) {
    FilterPreset.ORIGINAL -> "Original"
    FilterPreset.AUTO -> "Auto"
    FilterPreset.CLEAN -> "Clean"
    FilterPreset.COLOR -> "Color"
    FilterPreset.GRAYSCALE -> "Grayscale"
    FilterPreset.BLACK_AND_WHITE -> "B&W"
    FilterPreset.RECEIPT -> "Receipt"
    FilterPreset.BOOK -> "Book"
    FilterPreset.WHITEBOARD -> "Whiteboard"
    FilterPreset.PHOTO_DOCUMENT -> "PhotoDocument"
}
