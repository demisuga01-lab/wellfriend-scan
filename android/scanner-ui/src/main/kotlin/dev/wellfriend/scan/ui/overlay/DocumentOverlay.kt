package dev.wellfriend.scan.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.wellfriend.scan.core.CaptureGuidance
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.core.PageGeometry
import dev.wellfriend.scan.core.Point2D
import dev.wellfriend.scan.core.PreviewCoordinateMapper
import dev.wellfriend.scan.core.PreviewScaleMode
import dev.wellfriend.scan.core.PreviewSize

@Composable
fun LiveDocumentOverlay(
    geometry: PageGeometry?,
    imageSize: ImageSize?,
    rotationDegrees: Int,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val quad = geometry ?: return@Canvas
        val size = imageSize ?: return@Canvas
        val mapper = PreviewCoordinateMapper(
            imageSize = size,
            previewSize = PreviewSize(this.size.width, this.size.height),
            rotationDegrees = rotationDegrees,
            scaleMode = PreviewScaleMode.FILL,
            mirrored = mirrored,
        )
        val points = mapper.imageToPreview(quad)
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, color = Color(0xFF41D68A), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
        points.forEach { point -> drawCircle(Color.White, radius = 8f, center = Offset(point.x, point.y)) }
    }
}

@Composable
fun CaptureGuidancePanel(guidance: List<CaptureGuidance>, confidence: Float?, modifier: Modifier = Modifier) {
    val message = guidance.firstOrNull()?.let(::guidanceText) ?: "Searching for document"
    Text(
        text = if (confidence == null) message else "$message · ${(confidence * 100).toInt()}%",
        modifier = modifier
            .background(Color(0xAA111111), MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = Color.White,
    )
}

/** Four-handle editor: output remains source-image coordinates and must be revalidated by ScanController. */
@Composable
fun ManualCropEditor(
    geometry: PageGeometry,
    onApply: (List<Point2D>) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var corners by remember(geometry) { mutableStateOf(geometry.corners) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val mapper = remember(canvasSize, geometry.imageSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) null else PreviewCoordinateMapper(
            geometry.imageSize,
            PreviewSize(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
            0,
            PreviewScaleMode.FIT,
            false,
        )
    }
    var activeCorner by remember { mutableStateOf<Int?>(null) }
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(360.dp).background(Color.DarkGray)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(corners, mapper) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                activeCorner = mapper?.let { mapping ->
                                    val previewCorners = corners.map(mapping::imageToPreview)
                                    previewCorners.indices.minByOrNull { index -> distance(previewCorners[index], offset) }
                                }
                            },
                            onDragEnd = { activeCorner = null },
                            onDragCancel = { activeCorner = null },
                            onDrag = { change, _ ->
                                change.consume()
                                val index = activeCorner ?: return@detectDragGestures
                                val mapping = mapper ?: return@detectDragGestures
                                val source = mapping.previewToImage(Point2D(change.position.x, change.position.y))
                                val clamped = Point2D(
                                    source.x.coerceIn(0f, geometry.imageSize.width.toFloat()),
                                    source.y.coerceIn(0f, geometry.imageSize.height.toFloat()),
                                )
                                corners = corners.toMutableList().also { it[index] = clamped }
                            },
                        )
                    },
            ) {
                val mapping = mapper ?: return@Canvas
                val points = corners.map(mapping::imageToPreview)
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, Color(0xFF41D68A), style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
                points.forEach { drawCircle(Color.White, 12f, Offset(it.x, it.y)) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Button(onClick = onReset) { Text("Reset") }
            Button(onClick = { onApply(corners) }) { Text("Apply crop") }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun distance(point: Point2D, offset: Offset): Float {
    val x = point.x - offset.x
    val y = point.y - offset.y
    return x * x + y * y
}

private fun guidanceText(guidance: CaptureGuidance): String = when (guidance) {
    CaptureGuidance.NO_DOCUMENT -> "Position a document in view"
    CaptureGuidance.MOVE_CLOSER -> "Move closer"
    CaptureGuidance.MOVE_FARTHER -> "Move farther"
    CaptureGuidance.HOLD_STEADY -> "Hold steady"
    CaptureGuidance.TOO_DARK -> "More light needed"
    CaptureGuidance.TOO_BRIGHT -> "Reduce bright light"
    CaptureGuidance.TOO_BLURRY -> "Image is blurry"
    CaptureGuidance.GLARE_DETECTED -> "Reduce glare"
    CaptureGuidance.DOCUMENT_CUT_OFF -> "Keep all borders in view"
    CaptureGuidance.LOW_CONFIDENCE -> "Keep the page in view"
    CaptureGuidance.LOW_DETECTOR_AGREEMENT -> "Hold still for a clearer page"
    CaptureGuidance.READY -> "Ready"
    CaptureGuidance.CAPTURING -> "Capturing"
}
