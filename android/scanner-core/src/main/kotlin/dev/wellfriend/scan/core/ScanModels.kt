package dev.wellfriend.scan.core

/** Scanner product state; perception algorithms stay outside this module. */
enum class ScannerState {
    IDLE,
    REQUESTING_PERMISSION,
    CAMERA_STARTING,
    SEARCHING_FOR_DOCUMENT,
    DOCUMENT_CANDIDATE_FOUND,
    ALMOST_READY,
    READY,
    CAPTURING,
    CAPTURED,
    REVIEWING_PAGE,
    EDITING_CROP,
    APPLYING_FILTER,
    PAGE_ACCEPTED,
    EXPORTING,
    ERROR,
}

enum class CaptureMode { AUTO, MANUAL, BATCH, GALLERY }

/** These values mirror MP3 CaptureGuidance, with Android-specific casing only. */
enum class CaptureGuidance {
    NO_DOCUMENT,
    MOVE_CLOSER,
    MOVE_FARTHER,
    HOLD_STEADY,
    TOO_DARK,
    TOO_BRIGHT,
    TOO_BLURRY,
    GLARE_DETECTED,
    DOCUMENT_CUT_OFF,
    LOW_CONFIDENCE,
    LOW_DETECTOR_AGREEMENT,
    READY,
    CAPTURING,
}

enum class FilterPreset {
    ORIGINAL,
    AUTO,
    CLEAN,
    COLOR,
    GRAYSCALE,
    BLACK_AND_WHITE,
    RECEIPT,
    BOOK,
    WHITEBOARD,
    PHOTO_DOCUMENT,
}

enum class PageState { DRAFT, CAPTURING, RECONSTRUCTING, READY, FAILED }
enum class ProcessingStatus { IDLE, QUEUED, RUNNING, COMPLETE, FAILED }
enum class GeometrySource { AUTO, MANUAL }

data class ImageSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "image dimensions must be positive" }
    }
}

data class Point2D(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "point coordinates must be finite" }
    }
}

/** Quad in source-image pixels, ordered top-left, top-right, bottom-right, bottom-left. */
data class PageGeometry(
    val corners: List<Point2D>,
    val imageSize: ImageSize,
    val confidence: Float,
    val source: GeometrySource,
) {
    init {
        require(corners.size == 4) { "a page geometry must have exactly four corners" }
        require(confidence.isFinite() && confidence in 0f..1f) { "confidence must be bounded" }
        require(corners.all { it.x in 0f..imageSize.width.toFloat() && it.y in 0f..imageSize.height.toFloat() }) {
            "page corners must stay inside the source image"
        }
        require(abs(signedArea(corners)) > 1f) { "page geometry must have non-zero area" }
        require(isConvex(corners)) { "page geometry must be convex and non-self-intersecting" }
    }

    fun rotatedClockwise(): PageGeometry = copy(corners = listOf(corners[3], corners[0], corners[1], corners[2]))

    companion object {
        fun validate(
            corners: List<Point2D>,
            imageSize: ImageSize,
            confidence: Float,
            source: GeometrySource,
        ): Result<PageGeometry> = runCatching { PageGeometry(corners, imageSize, confidence, source) }

        private fun signedArea(points: List<Point2D>): Float =
            points.indices.sumOf { index ->
                val next = points[(index + 1) % points.size]
                (points[index].x * next.y - next.x * points[index].y).toDouble()
            }.toFloat() * 0.5f

        private fun isConvex(points: List<Point2D>): Boolean {
            val crossProducts = points.indices.map { index ->
                val a = points[index]
                val b = points[(index + 1) % points.size]
                val c = points[(index + 2) % points.size]
                (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            }
            return crossProducts.all { it > 0f } || crossProducts.all { it < 0f }
        }
    }
}

data class CanonicalPagePreview(
    val uri: String,
    val size: ImageSize,
    val diagnostics: List<String> = emptyList(),
)

data class ScanPage(
    val id: String,
    val sourceUri: String,
    val sourceSize: ImageSize,
    val thumbnailUri: String? = null,
    val detectedGeometry: PageGeometry? = null,
    val manualGeometry: PageGeometry? = null,
    val canonicalPage: CanonicalPagePreview? = null,
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val rotationDegrees: Int = 0,
    val state: PageState = PageState.DRAFT,
    val processingStatus: ProcessingStatus = ProcessingStatus.IDLE,
    val diagnostics: List<String> = emptyList(),
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "rotation must be 0, 90, 180, or 270" }
    }

    val effectiveGeometry: PageGeometry? get() = manualGeometry ?: detectedGeometry
}

data class ExportOptions(
    val format: String = "PDF_PLACEHOLDER",
    val includeOcr: Boolean = false,
    val quality: String = "balanced",
)

data class ScanSession(
    val id: String,
    val domain: String = "document",
    val pages: List<ScanPage> = emptyList(),
    val exportOptions: ExportOptions = ExportOptions(),
)

/** Pure session mutations make page review behavior host-testable and Compose-independent. */
object ScanSessionReducer {
    fun addPage(session: ScanSession, page: ScanPage): ScanSession {
        require(session.pages.none { it.id == page.id }) { "page id already exists" }
        return session.copy(pages = session.pages + page)
    }

    fun deletePage(session: ScanSession, pageId: String): ScanSession =
        session.copy(pages = session.pages.filterNot { it.id == pageId })

    fun reorderPage(session: ScanSession, fromIndex: Int, toIndex: Int): ScanSession {
        require(fromIndex in session.pages.indices && toIndex in session.pages.indices) { "page index out of bounds" }
        val pages = session.pages.toMutableList()
        val page = pages.removeAt(fromIndex)
        pages.add(toIndex, page)
        return session.copy(pages = pages)
    }

    fun updatePage(session: ScanSession, page: ScanPage): ScanSession {
        require(session.pages.any { it.id == page.id }) { "page does not exist" }
        return session.copy(pages = session.pages.map { if (it.id == page.id) page else it })
    }

    fun rotatePage(session: ScanSession, pageId: String): ScanSession = updatePage(
        session,
        page(session, pageId).copy(rotationDegrees = (page(session, pageId).rotationDegrees + 90) % 360),
    )

    fun page(session: ScanSession, pageId: String): ScanPage =
        session.pages.firstOrNull { it.id == pageId } ?: error("page does not exist")
}

private fun abs(value: Float): Float = if (value < 0f) -value else value
