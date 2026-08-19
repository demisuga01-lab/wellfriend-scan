package dev.wellfriend.scan.core

enum class PreviewScaleMode { FIT, FILL }

data class PreviewSize(val width: Float, val height: Float) {
    init { require(width > 0f && height > 0f) { "preview dimensions must be positive" } }
}

/**
 * Maps MP3/MP4 source-image coordinates to a CameraX preview without baking UI geometry into
 * perception. It handles image rotation, letterboxing/cropping, and optional front-camera mirror.
 */
class PreviewCoordinateMapper(
    private val imageSize: ImageSize,
    private val previewSize: PreviewSize,
    private val rotationDegrees: Int,
    private val scaleMode: PreviewScaleMode,
    private val mirrored: Boolean,
) {
    init { require(rotationDegrees in setOf(0, 90, 180, 270)) { "preview rotation is invalid" } }

    private val rotatedSize = if (rotationDegrees == 90 || rotationDegrees == 270) {
        PreviewSize(imageSize.height.toFloat(), imageSize.width.toFloat())
    } else {
        PreviewSize(imageSize.width.toFloat(), imageSize.height.toFloat())
    }
    private val scale = when (scaleMode) {
        PreviewScaleMode.FIT -> minOf(previewSize.width / rotatedSize.width, previewSize.height / rotatedSize.height)
        PreviewScaleMode.FILL -> maxOf(previewSize.width / rotatedSize.width, previewSize.height / rotatedSize.height)
    }
    private val offsetX = (previewSize.width - rotatedSize.width * scale) / 2f
    private val offsetY = (previewSize.height - rotatedSize.height * scale) / 2f

    fun imageToPreview(point: Point2D): Point2D {
        val rotated = rotate(point)
        val x = if (mirrored) rotatedSize.width - rotated.x else rotated.x
        return Point2D(offsetX + x * scale, offsetY + rotated.y * scale)
    }

    fun previewToImage(point: Point2D): Point2D {
        val unscaledX = (point.x - offsetX) / scale
        val unscaledY = (point.y - offsetY) / scale
        val rotated = Point2D(if (mirrored) rotatedSize.width - unscaledX else unscaledX, unscaledY)
        return inverseRotate(rotated)
    }

    fun imageToPreview(geometry: PageGeometry): List<Point2D> = geometry.corners.map(::imageToPreview)

    private fun rotate(point: Point2D): Point2D = when (rotationDegrees) {
        0 -> point
        90 -> Point2D(imageSize.height - point.y, point.x)
        180 -> Point2D(imageSize.width - point.x, imageSize.height - point.y)
        270 -> Point2D(point.y, imageSize.width - point.x)
        else -> error("validated rotation")
    }

    private fun inverseRotate(point: Point2D): Point2D = when (rotationDegrees) {
        0 -> point
        90 -> Point2D(point.y, imageSize.height - point.x)
        180 -> Point2D(imageSize.width - point.x, imageSize.height - point.y)
        270 -> Point2D(imageSize.width - point.y, point.x)
        else -> error("validated rotation")
    }
}

/** Converts analysis geometry into high-resolution ImageCapture coordinates before reconstruction. */
object GeometryMapper {
    fun mapToImageSize(geometry: PageGeometry, target: ImageSize): PageGeometry = PageGeometry(
        corners = geometry.corners.map { point ->
            Point2D(
                point.x * target.width / geometry.imageSize.width,
                point.y * target.height / geometry.imageSize.height,
            )
        },
        imageSize = target,
        confidence = geometry.confidence,
        source = geometry.source,
    )
}
