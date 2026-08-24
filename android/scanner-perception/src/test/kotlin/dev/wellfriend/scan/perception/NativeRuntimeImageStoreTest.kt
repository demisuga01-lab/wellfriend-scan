package dev.wellfriend.scan.perception

import dev.wellfriend.scan.core.FilterPreset
import dev.wellfriend.scan.core.ImageSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeRuntimeImageStoreTest {
    private val imageJson = """{"schema_version":1,"image":{"width":2,"height":2,"stride":2,"pixel_format":"Gray8","bytes":[1,2,3,4]},"confidence":0.8,"diagnostics":["fixture"]}"""

    @Test fun `native reconstruction response registers its decoded result for a subsequent scalar filter`() {
        val store = NativeRuntimeImageStore()
        val reconstructed = NativeJsonMapper.reconstruction(imageJson, store)
        assertEquals(ImageSize(2, 2), reconstructed.outputSize)
        assertEquals(4, store.resolve(reconstructed.outputUri).bytes.size)
        val filtered = NativeJsonMapper.filter(
            """{"schema_version":1,"image":{"width":2,"height":2,"stride":2,"pixel_format":"Gray8","bytes":[2,3,4,5]},"applied_processor_ids":["grayscale"],"diagnostics":["filtered"]}""",
            store,
        )
        assertEquals(listOf("grayscale"), filtered.appliedProcessorIds)
        assertEquals(ImageSize(2, 2), filtered.outputSize)
        assertEquals(4, store.resolve(filtered.outputUri).bytes.size)
    }

    @Test fun `unregistered URI fails closed instead of attempting to decode a path`() {
        assertFailsWith<NativePerceptionUnavailableException> {
            NativeRuntimeImageStore().resolve("content://not-registered", ImageSize(2, 2))
        }
    }

    @Test fun `filter names retain the compatible Wellfriend scalar preset spelling`() {
        assertEquals("B&W", FilterPreset.BLACK_AND_WHITE.runtimeName())
    }
}
