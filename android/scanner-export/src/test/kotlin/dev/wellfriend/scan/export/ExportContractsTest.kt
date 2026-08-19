package dev.wellfriend.scan.export

import dev.wellfriend.scan.core.ScanSession
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ExportContractsTest {
    @Test fun `debug export rejects an empty scan session`() {
        val request = ExportRequest(ScanSession("empty"), ExportFormat.JSON_DEBUG, File("build/test-empty-export"))
        val error = ScanExporter.validate(request).exceptionOrNull()
        assertTrue(error is ExportError.EmptySession)
    }
}
