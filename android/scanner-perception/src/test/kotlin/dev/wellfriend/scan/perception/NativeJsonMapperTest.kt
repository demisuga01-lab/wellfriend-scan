package dev.wellfriend.scan.perception

import dev.wellfriend.scan.core.ImageSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NativeJsonMapperTest {
    @Test fun `native scalar quad response maps without a regex error`() {
        val response = """
            {"schema_version":1,"engine_mode":"native_scalar","fused_quad":null,
             "refined_quad":{"points":[
               {"x":20.0,"y":30.0},{"x":180.0,"y":30.0},
               {"x":180.0,"y":90.0},{"x":20.0,"y":90.0}
             ]},"capture_readiness":"READY","capture_readiness_score":0.8,
             "guidance":["READY"],"diagnostics":["visible_edge_found"]}
        """.trimIndent()

        val frame = PerceptionFrame(
            frameId = 1,
            timestampMillis = 0,
            size = ImageSize(200, 120),
            rotationDegrees = 90,
            pixelFormat = PerceptionPixelFormat.GRAY8,
            rowStrideBytes = 200,
            bytes = ByteArray(200 * 120),
            source = "camera",
            mirrored = false,
        )

        val result = NativeJsonMapper.analyze(response, frame)

        assertEquals(1, NativeJsonMapper.schemaVersion(response))
        assertEquals(PerceptionEngineMode.NATIVE, result.engineMode)
        assertEquals(0.8f, result.captureReadinessScore)
        assertNotNull(result.refinementResult.geometry)
        assertFalse(result.diagnostics.contains("mock_used=true"))
        assertEquals("mock_used=false", result.diagnostics.last())
    }
}
