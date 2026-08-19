package dev.wellfriend.scan

import android.Manifest
import android.os.Bundle
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
                scanController.onCameraStarted()
                scanController.analyzeFrame(frame)
            },
            onCameraError = { scanController.onCameraFailure(it) },
        )
        imuHooks = ImuCaptureHooks(this) { /* MP7 records the hook; MP3 temporal fusion owns use. */ }
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
