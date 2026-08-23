package dev.wellfriend.scan.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScanSessionReducerTest {
    private val size = ImageSize(100, 200)
    private fun page(id: String) = ScanPage(id, "file:///$id.jpg", size)

    @Test fun `pages add delete reorder and rotate without image bytes`() {
        val start = ScanSession("session")
        val withPages = ScanSessionReducer.addPage(ScanSessionReducer.addPage(start, page("a")), page("b"))
        assertEquals(listOf("a", "b"), withPages.pages.map { it.id })
        val reordered = ScanSessionReducer.reorderPage(withPages, 1, 0)
        assertEquals(listOf("b", "a"), reordered.pages.map { it.id })
        assertEquals(90, ScanSessionReducer.rotatePage(reordered, "b").pages.first().rotationDegrees)
        assertEquals(listOf("a"), ScanSessionReducer.deletePage(reordered, "b").pages.map { it.id })
    }

    @Test fun `page geometry requires bounded convex-like nonzero source coordinates`() {
        val valid = PageGeometry.validate(
            listOf(Point2D(5f, 5f), Point2D(95f, 5f), Point2D(95f, 195f), Point2D(5f, 195f)),
            size,
            0.8f,
            GeometrySource.MANUAL,
        )
        assertTrue(valid.isSuccess)
        assertTrue(PageGeometry.validate(List(4) { Point2D(0f, 0f) }, size, 1f, GeometrySource.MANUAL).isFailure)
        assertFailsWith<IllegalArgumentException> { PageGeometry(List(3) { Point2D(0f, 0f) }, size, 1f, GeometrySource.MANUAL) }
    }

    @Test fun `scanner options keep host feature and filter choices explicit`() {
        val options = WellfriendScannerOptions(
            enabledFeatures = ScannerFeatureSet(torch = false, galleryImport = false),
            filters = listOf(FilterPreset.ORIGINAL, FilterPreset.GRAYSCALE),
            defaultFilter = FilterPreset.GRAYSCALE,
            multiPageEnabled = false,
        )
        assertEquals(FilterPreset.GRAYSCALE, options.defaultFilter)
        assertTrue(!options.enabledFeatures.torch)
        assertTrue(!options.multiPageEnabled)
        assertFailsWith<IllegalArgumentException> {
            WellfriendScannerOptions(filters = listOf(FilterPreset.ORIGINAL), defaultFilter = FilterPreset.CLEAN)
        }
    }
}
