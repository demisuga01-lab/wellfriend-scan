package dev.wellfriend.scan.integration

import dev.wellfriend.scan.core.CaptureMode
import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.core.Point2D
import dev.wellfriend.scan.export.ExportFormat
import dev.wellfriend.scan.export.ExportRequest
import dev.wellfriend.scan.export.ScanExporter
import dev.wellfriend.scan.perception.DevMockPerceptionEngine
import dev.wellfriend.scan.perception.PerceptionFrame
import dev.wellfriend.scan.perception.PerceptionPixelFormat
import dev.wellfriend.scan.perception.ScanController
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Host-level product flow: synthetic image -> perception -> capture -> crop -> filter -> debug export. */
class AndroidHostFlowTest {
    @Test fun `scan flow remains bound to the perception engine`() = runBlocking {
        val controller = ScanController(DevMockPerceptionEngine())
        controller.onPermissionResult(true)
        controller.onCameraStarted()
        controller.analyzeFrame(
            PerceptionFrame(1, 1, ImageSize(100, 200), 0, PerceptionPixelFormat.GRAY8, 100, ByteArray(100) { 1 }, "synthetic"),
        )
        assertTrue(controller.requestCapture(CaptureMode.AUTO))
        val page = controller.onPhotoCaptured("file:///synthetic.jpg", ImageSize(1000, 2000))
        assertTrue(controller.applyManualCrop(page.id, listOf(
            Point2D(20f, 20f), Point2D(980f, 20f), Point2D(980f, 1980f), Point2D(20f, 1980f),
        )).isSuccess)
        controller.reconstructPage(page.id)
        controller.applyFilter(page.id, FilterPreset.CLEAN)
        val output = Files.createTempDirectory("wf-scan-integration").toFile()
        val result = ScanExporter.exportDebugJson(ExportRequest(controller.state.value.session, ExportFormat.JSON_DEBUG, output))
        assertTrue(result.outputFiles.single().isFile)
    }
}
