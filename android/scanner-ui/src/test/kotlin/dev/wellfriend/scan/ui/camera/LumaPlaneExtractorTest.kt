package dev.wellfriend.scan.ui.camera

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class LumaPlaneExtractorTest {
    @Test fun `crop compacts padded CameraX luma rows into the exact Rust source coordinates`() {
        val source = byteArrayOf(
            0, 1, 2, 3, 99, 99,
            10, 11, 12, 13, 99, 99,
            20, 21, 22, 23, 99, 99,
        )
        assertContentEquals(byteArrayOf(11, 12, 21, 22), LumaPlaneExtractor.compact(source, 6, 1, 1, 1, 2, 2))
    }

    @Test fun `pixel stride two is compacted without leaking padding`() {
        val source = byteArrayOf(0, 99, 1, 99, 2, 99, 3, 99)
        assertContentEquals(byteArrayOf(1, 2), LumaPlaneExtractor.compact(source, 8, 2, 1, 0, 2, 1))
    }

    @Test fun `out of bounds crop fails before a malformed frame can reach Rust`() {
        assertFailsWith<IllegalArgumentException> { LumaPlaneExtractor.compact(ByteArray(4), 4, 1, 3, 0, 2, 1) }
    }
}
