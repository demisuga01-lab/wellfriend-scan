package dev.wellfriend.scan.ui.camera

import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    fun bind(previewView: PreviewView, frontCamera: Boolean = false) {
        this.previewView = previewView
        lensFacing = if (frontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                runCatching { bindUseCases(future.get()) }
                    .onFailure { onCameraError(it.message ?: "unable to bind CameraX use cases") }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun switchCamera() {
        val view = previewView ?: return
        bind(view, lensFacing != CameraSelector.LENS_FACING_FRONT)
    }

    fun setTorch(enabled: Boolean) {
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
            .also { it.surfaceProvider = view.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(cameraExecutor) { proxy ->
                    val frame = try {
                        ImageProxyFrameConverter.copyForAnalysis(proxy, frameIds.incrementAndGet())
                    } finally {
                        proxy.close()
                    }
                    if (frame != null) analysisScope.launch { onFrame(frame) }
                }
            }
        val capture = ImageCapture.Builder()
            .setTargetRotation(view.display?.rotation ?: Surface.ROTATION_0)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        imageCapture = capture
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis, capture)
    }
}

/** Copies luma to a compact Gray8 DTO before ImageProxy is closed; conversion is not detector logic. */
object ImageProxyFrameConverter {
    fun copyForAnalysis(proxy: ImageProxy, frameId: Long): PerceptionFrame? {
        val plane = proxy.planes.firstOrNull() ?: return null
        val source = plane.buffer.duplicate()
        if (!source.hasRemaining()) return null
        val bytes = ByteArray(source.remaining())
        source.get(bytes)
        return PerceptionFrame(
            frameId = frameId,
            timestampMillis = System.currentTimeMillis(),
            size = ImageSize(proxy.width, proxy.height),
            rotationDegrees = proxy.imageInfo.rotationDegrees,
            pixelFormat = PerceptionPixelFormat.GRAY8,
            rowStrideBytes = plane.rowStride,
            bytes = bytes,
            source = "camera",
            mirrored = false,
        )
    }
}
