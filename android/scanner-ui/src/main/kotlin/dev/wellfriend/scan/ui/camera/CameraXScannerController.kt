package dev.wellfriend.scan.ui.camera

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExposureState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.perception.PerceptionFrame
import dev.wellfriend.scan.perception.PerceptionPixelFormat
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Runtime facts shown in the debug panel and written with stable Wellfriend log tags. */
data class CameraRuntimeDiagnostics(
    val permissionGranted: Boolean? = null,
    val previewViewCreated: Boolean = false,
    val previewSurfaceAttached: Boolean = false,
    val providerObtained: Boolean = false,
    val useCasesBound: Boolean = false,
    val lensFacing: String = "BACK",
    val frameCount: Long = 0,
    val lastFrame: String? = null,
    val lastError: String? = null,
)

/** Owns CameraX use cases; it intentionally delegates every perception decision through onFrame. */
class CameraXScannerController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: suspend (PerceptionFrame) -> Unit,
    private val onCameraError: (String) -> Unit,
) : AutoCloseable {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val frameIds = AtomicLong(0)
    private val analysisInFlight = AtomicBoolean(false)
    private val mutableDiagnostics = MutableStateFlow(CameraRuntimeDiagnostics())
    val diagnostics: StateFlow<CameraRuntimeDiagnostics> = mutableDiagnostics.asStateFlow()
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var bindingPending = false

    fun onPreviewViewCreated(view: PreviewView) {
        previewView = view
        updateDiagnostics { it.copy(previewViewCreated = true) }
    }

    fun onPermissionResult(granted: Boolean) {
        Log.i("WellfriendCamera", "camera permission granted=$granted")
        updateDiagnostics { it.copy(permissionGranted = granted, lastError = if (granted) null else "camera permission denied") }
    }

    fun bind(previewView: PreviewView, frontCamera: Boolean = false) {
        val nextLensFacing = if (frontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        if (this.previewView === previewView && bindingPending) return
        if (this.previewView === previewView && camera != null && lensFacing == nextLensFacing) return
        this.previewView = previewView
        lensFacing = nextLensFacing
        bindingPending = true
        Log.i("WellfriendCamera", "requesting CameraX bind selector=${lensName(lensFacing)}")
        updateDiagnostics { it.copy(lensFacing = lensName(lensFacing), lastError = null) }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching {
                    val provider = future.get()
                    cameraProvider = provider
                    Log.i("WellfriendCamera", "ProcessCameraProvider obtained")
                    updateDiagnostics { it.copy(providerObtained = true) }
                    bindUseCases(provider)
                }.onFailure { failure ->
                    bindingPending = false
                    reportCameraError(failure.message ?: "unable to bind CameraX use cases")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun unbind(view: PreviewView? = null) {
        if (view != null && previewView !== view) return
        if (camera == null && !bindingPending) return
        Log.i("WellfriendCamera", "unbinding CameraX use cases")
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        bindingPending = false
        updateDiagnostics { it.copy(previewSurfaceAttached = false, useCasesBound = false) }
    }

    fun switchCamera() {
        val view = previewView ?: return
        bind(view, lensFacing != CameraSelector.LENS_FACING_FRONT)
    }

    fun setTorch(enabled: Boolean) {
        Log.i("WellfriendCamera", "torch requested enabled=$enabled")
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setZoom(linearZoom: Float) {
        camera?.cameraControl?.setLinearZoom(linearZoom.coerceIn(0f, 1f))
    }

    fun setExposureCompensation(index: Int) {
        val state: ExposureState = camera?.cameraInfo?.exposureState ?: return
        camera?.cameraControl?.setExposureCompensationIndex(index.coerceIn(
            state.exposureCompensationRange.lower,
            state.exposureCompensationRange.upper,
        ))
    }

    fun focusAt(previewX: Float, previewY: Float) {
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(previewX, previewY)
        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
    }

    /** Writes a high-quality ImageCapture result to disk; pages retain its URI rather than bitmap bytes. */
    fun takeHighResolutionPhoto(output: File, onSaved: (File) -> Unit, onError: (String) -> Unit) {
        val capture = imageCapture ?: return onError("camera capture is not ready")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(output).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = onSaved(output)
                override fun onError(exception: ImageCaptureException) = onError(exception.message ?: "image capture failed")
            },
        )
    }

    override fun close() {
        unbind()
        analysisScope.cancel()
        cameraExecutor.shutdown()
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        val view = requireNotNull(previewView) { "preview view has not been provided" }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        if (!provider.hasCamera(selector)) {
            throw IllegalStateException("selected camera is not available")
        }
        val preview = Preview.Builder().setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0).build()
            .also {
                it.setSurfaceProvider(view.surfaceProvider)
                Log.i("WellfriendPreview", "Preview surface provider attached rotation=${view.display?.rotation ?: Surface.ROTATION_0}")
                updateDiagnostics { state -> state.copy(previewSurfaceAttached = true) }
            }
        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(cameraExecutor) { proxy ->
                    val frameNumber = frameIds.incrementAndGet()
                    val luma = proxy.planes.firstOrNull()
                    if (frameNumber == 1L || frameNumber % 60L == 0L) {
                        Log.i(
                            "WellfriendAnalysis",
                            "ImageAnalysis frame=$frameNumber size=${proxy.width}x${proxy.height} crop=${proxy.cropRect} rotation=${proxy.imageInfo.rotationDegrees} rowStride=${luma?.rowStride} pixelStride=${luma?.pixelStride}",
                        )
                    }
                    val frame = try {
                        ImageProxyFrameConverter.copyForAnalysis(
                            proxy,
                            frameNumber,
                            mirrored = lensFacing == CameraSelector.LENS_FACING_FRONT,
                        )
                    } finally {
                        proxy.close()
                    }
                    if (frame != null) {
                        if (frameNumber == 1L || frameNumber % 60L == 0L) {
                            Log.i(
                                "WellfriendAnalysis",
                                "Gray8 mapped frame=$frameNumber size=${frame.size.width}x${frame.size.height} stride=${frame.rowStrideBytes} rotation=${frame.rotationDegrees}",
                            )
                        }
                        updateDiagnostics {
                            it.copy(frameCount = frameNumber, lastFrame = "${frame.size.width}x${frame.size.height} Gray8 stride=${frame.rowStrideBytes} rotation=${frame.rotationDegrees}")
                        }
                        if (analysisInFlight.compareAndSet(false, true)) {
                            analysisScope.launch {
                                try {
                                    onFrame(frame)
                                } catch (failure: Exception) {
                                    reportCameraError(failure.message ?: "native frame analysis failed")
                                } finally {
                                    analysisInFlight.set(false)
                                }
                            }
                        }
                    }
                }
            }
        val capture = ImageCapture.Builder()
            .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture = capture
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis, capture)
        bindingPending = false
        Log.i(
            "WellfriendCamera",
            "use cases bound selector=${lensName(lensFacing)} torch=${camera?.cameraInfo?.torchState?.value} control=${camera?.cameraControl != null}",
        )
        updateDiagnostics { it.copy(useCasesBound = true, lensFacing = lensName(lensFacing), lastError = null) }
    }

    private fun reportCameraError(message: String) {
        Log.e("WellfriendCamera", message)
        updateDiagnostics { it.copy(lastError = message, useCasesBound = false) }
        onCameraError(message)
    }

    private fun updateDiagnostics(transform: (CameraRuntimeDiagnostics) -> CameraRuntimeDiagnostics) {
        mutableDiagnostics.value = transform(mutableDiagnostics.value)
    }

    private fun lensName(lensFacing: Int): String = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONT" else "BACK"
}

/** Copies a crop-aware luma image to compact Gray8 before ImageProxy is closed. */
object ImageProxyFrameConverter {
    fun copyForAnalysis(proxy: ImageProxy, frameId: Long, mirrored: Boolean): PerceptionFrame? {
        val plane = proxy.planes.firstOrNull() ?: return null
        val source = plane.buffer.duplicate()
        source.rewind()
        if (!source.hasRemaining()) return null
        val rawBytes = ByteArray(source.remaining())
        source.get(rawBytes)
        val crop = proxy.cropRect
        val compact = runCatching {
            LumaPlaneExtractor.compact(
                rawBytes,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                crop = crop,
            )
        }.getOrNull() ?: return null
        return PerceptionFrame(
            frameId = frameId,
            timestampMillis = System.currentTimeMillis(),
            size = ImageSize(crop.width(), crop.height()),
            rotationDegrees = proxy.imageInfo.rotationDegrees,
            pixelFormat = PerceptionPixelFormat.GRAY8,
            rowStrideBytes = crop.width(),
            bytes = compact,
            source = "camera",
            mirrored = mirrored,
        )
    }
}

/** Pure copy logic: coordinates sent to Rust are exactly the compact crop pixels shown by overlay mapping. */
object LumaPlaneExtractor {
    fun compact(source: ByteArray, rowStride: Int, pixelStride: Int, crop: Rect): ByteArray =
        compact(source, rowStride, pixelStride, crop.left, crop.top, crop.width(), crop.height())

    /** Android-free overload used by host tests to calibrate CameraX luma/crop coordinates. */
    fun compact(source: ByteArray, rowStride: Int, pixelStride: Int, left: Int, top: Int, width: Int, height: Int): ByteArray {
        require(rowStride > 0 && pixelStride > 0) { "invalid luma plane stride" }
        require(width > 0 && height > 0 && left >= 0 && top >= 0) { "invalid camera crop" }
        val last = (top + height - 1) * rowStride + (left + width - 1) * pixelStride
        require(last in source.indices) { "camera crop exceeds luma plane" }
        return ByteArray(width * height).also { output ->
            var write = 0
            repeat(height) { row ->
                val rowStart = (top + row) * rowStride + left * pixelStride
                repeat(width) { column -> output[write++] = source[rowStart + column * pixelStride] }
            }
        }
    }
}
