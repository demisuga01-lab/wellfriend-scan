package dev.wellfriend.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.wellfriend.scan.core.CaptureMode
import dev.wellfriend.scan.export.ExportFormat
import dev.wellfriend.scan.export.ExportRequest
import dev.wellfriend.scan.export.ScanExporter
import dev.wellfriend.scan.perception.PerceptionEngineFactory
import dev.wellfriend.scan.perception.NativeRuntimeImage
import dev.wellfriend.scan.perception.NativeRuntimeImageStore
import dev.wellfriend.scan.perception.NativeRuntimeArtifactDiagnostics
import dev.wellfriend.scan.perception.NativeRuntimeLogger
import dev.wellfriend.scan.perception.ScanController
import dev.wellfriend.scan.ui.WellfriendScannerApp
import dev.wellfriend.scan.ui.camera.CameraXScannerController
import dev.wellfriend.scan.ui.camera.ImuCaptureHooks
import java.io.File
import kotlinx.coroutines.launch

/** Reference Android product shell. Perception remains behind PerceptionEngine. */
class MainActivity : ComponentActivity() {
    private lateinit var scanController: ScanController
    private lateinit var cameraController: CameraXScannerController
    private lateinit var imuHooks: ImuCaptureHooks
    private val runtimeImages = NativeRuntimeImageStore()

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i("WellfriendCamera", "camera permission result granted=$granted")
        if (::cameraController.isInitialized) cameraController.onPermissionResult(granted)
        scanController.onPermissionResult(granted)
    }
    private val galleryPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        lifecycleScope.launch {
            GalleryImportDecoder(this@MainActivity).decode(uri)
                .onSuccess { imported ->
                    // The scalar bridge consumes this explicit, bounds-checked decoded image. It never reopens an arbitrary URI.
                    runtimeImages.register(uri.toString(), NativeRuntimeImage.fromFrame(imported.frame))
                    scanController.importGallery(uri.toString(), imported.frame.size, imported.frame)
                }
                .onFailure { scanController.onGalleryImportFailure(it.message ?: "gallery image could not be imported") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeRuntimeLogger.configure { level, message, throwable ->
            when (level) {
                NativeRuntimeLogger.Level.DEBUG -> Log.d("WellfriendNative", message)
                NativeRuntimeLogger.Level.INFO -> Log.i("WellfriendNative", message)
                NativeRuntimeLogger.Level.ERROR -> Log.e("WellfriendNative", message, throwable)
            }
        }
        runCatching {
            val manifest = assets.open("wellfriend-runtime/android/manifest.json").bufferedReader().use { it.readText() }
            val checksums = assets.open("wellfriend-runtime/android/checksums.json").bufferedReader().use { it.readText() }
            NativeRuntimeArtifactDiagnostics.configure(manifest, checksums)
        }
        scanController = ScanController(PerceptionEngineFactory.create(BuildConfig.DEBUG, runtimeImages))
        cameraController = CameraXScannerController(
            context = this,
            lifecycleOwner = this,
            onFrame = { frame ->
                Log.i(
                    "WellfriendNative",
                    "analyzeFrame called frame=${frame.frameId} size=${frame.size.width}x${frame.size.height} stride=${frame.rowStrideBytes} format=${frame.pixelFormat}",
                )
                scanController.onCameraStarted()
                val result = scanController.analyzeFrame(frame)
                Log.i(
                    "WellfriendNative",
                    "analyzeFrame result mode=${result.engineMode} guidance=${result.guidance} confidence=${result.captureReadinessScore}",
                )
            },
            onCameraError = { scanController.onCameraFailure(it) },
        )
        imuHooks = ImuCaptureHooks(this) { /* MP7 records the hook; MP3 temporal fusion owns use. */ }
        // ActivityResultContracts only calls its callback after an explicit launch. A camera grant
        // restored by Android before process start must still transition the controller into the
        // bindable state; otherwise PreviewView exists but no CameraX use cases are attached.
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.i("WellfriendCamera", "camera permission already granted at activity creation")
            cameraController.onPermissionResult(true)
            scanController.onPermissionResult(true)
        }
        setContent {
            WellfriendScannerApp(
                scanController = scanController,
                cameraController = cameraController,
                onRequestCameraPermission = {
                    scanController.requestPermission()
                    cameraPermission.launch(Manifest.permission.CAMERA)
                },
                onManualCapture = ::capturePhoto,
                onGalleryImport = { galleryPicker.launch(arrayOf("image/*")) },
                onExportDebug = ::exportDebugJson,
            )
        }
    }

    override fun onDestroy() {
        cameraController.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        imuHooks.start()
    }

    override fun onPause() {
        imuHooks.stop()
        super.onPause()
    }

    private fun capturePhoto() {
        if (!scanController.requestCapture(CaptureMode.MANUAL)) return
        val output = File(cacheDir, "captures/scan-${System.currentTimeMillis()}.jpg").also { it.parentFile?.mkdirs() }
        cameraController.takeHighResolutionPhoto(
            output = output,
            onSaved = { file ->
                lifecycleScope.launch {
                    GalleryImportDecoder(this@MainActivity).decodeFile(file)
                        .onSuccess { captured ->
                            val uri = file.toURI().toString()
                            runtimeImages.register(uri, NativeRuntimeImage.fromFrame(captured.frame))
                            scanController.onPhotoCaptured(uri, captured.frame.size)
                        }
                        .onFailure { scanController.onCameraFailure(it.message ?: "captured image could not be decoded") }
                }
            },
            onError = scanController::onCameraFailure,
        )
    }

    private fun exportDebugJson() {
        val request = ExportRequest(
            session = scanController.state.value.session,
            format = ExportFormat.JSON_DEBUG,
            outputDirectory = File(filesDir, "exports"),
        )
        ScanExporter.exportDebugJson(request)
    }
}
