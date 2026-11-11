package dev.wellfriend.scan.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import dev.wellfriend.scan.perception.NativeRuntimeImage
import dev.wellfriend.scan.perception.NativeRuntimeImageStore
import dev.wellfriend.scan.perception.PerceptionPixelFormat

/** Renders only pixels that the Android decoder or native runtime explicitly registered. */
@Composable
fun RuntimeImagePreview(
    uri: String?,
    runtimeImages: NativeRuntimeImageStore,
    modifier: Modifier = Modifier,
    contentDescription: String = "Scanned page preview",
    rotationDegrees: Int = 0,
) {
    val bitmap = remember(uri) {
        uri?.let { value -> runCatching { RuntimeImageBitmap.decode(runtimeImages.resolve(value)) }.getOrNull() }
    }
    if (bitmap == null) {
        Box(modifier = modifier.background(Color(0xFF20242A)), contentAlignment = Alignment.Center) {
            Text("Preview is unavailable for this page", color = Color.White)
        }
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.graphicsLayer { rotationZ = rotationDegrees.toFloat() },
            contentScale = ContentScale.Fit,
        )
    }
}

/** Android-only conversion for bounded runtime images. It never decodes paths or URLs. */
object RuntimeImageBitmap {
    fun decode(image: NativeRuntimeImage): Bitmap {
        val pixels = IntArray(image.width * image.height)
        var destination = 0
        repeat(image.height) { y ->
            val row = y * image.stride
            repeat(image.width) { x ->
                val offset = when (image.pixelFormat) {
                    PerceptionPixelFormat.GRAY8 -> row + x
                    PerceptionPixelFormat.RGB8, PerceptionPixelFormat.BGR8 -> row + x * 3
                    PerceptionPixelFormat.RGBA8 -> row + x * 4
                    PerceptionPixelFormat.YUV420 -> error("YUV420 is not a runtime bitmap format")
                }
                val argb = when (image.pixelFormat) {
                    PerceptionPixelFormat.GRAY8 -> {
                        val value = image.bytes[offset].toInt() and 0xff
                        0xff000000.toInt() or (value shl 16) or (value shl 8) or value
                    }
                    PerceptionPixelFormat.RGB8 -> {
                        val r = image.bytes[offset].toInt() and 0xff
                        val g = image.bytes[offset + 1].toInt() and 0xff
                        val b = image.bytes[offset + 2].toInt() and 0xff
                        0xff000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                    PerceptionPixelFormat.BGR8 -> {
                        val b = image.bytes[offset].toInt() and 0xff
                        val g = image.bytes[offset + 1].toInt() and 0xff
                        val r = image.bytes[offset + 2].toInt() and 0xff
                        0xff000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                    PerceptionPixelFormat.RGBA8 -> {
                        val r = image.bytes[offset].toInt() and 0xff
                        val g = image.bytes[offset + 1].toInt() and 0xff
                        val b = image.bytes[offset + 2].toInt() and 0xff
                        val a = image.bytes[offset + 3].toInt() and 0xff
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    PerceptionPixelFormat.YUV420 -> error("YUV420 is not a runtime bitmap format")
                }
                pixels[destination++] = argb
            }
        }
        return Bitmap.createBitmap(pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
    }
}
