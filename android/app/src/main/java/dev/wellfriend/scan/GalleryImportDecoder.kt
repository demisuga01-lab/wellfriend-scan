package dev.wellfriend.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.wellfriend.scan.core.ImageSize
import dev.wellfriend.scan.perception.PerceptionFrame
import dev.wellfriend.scan.perception.PerceptionPixelFormat
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GalleryImport(val sourceSize: ImageSize, val frame: PerceptionFrame)

/** Decodes gallery images defensively and supplies an analysis-sized copy, not a full in-memory page. */
class GalleryImportDecoder(private val context: Context) {
    suspend fun decode(uri: Uri): Result<GalleryImport> = withContext(Dispatchers.IO) { runCatching {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            require(descriptor.length < 0 || descriptor.length <= MAX_FILE_BYTES) { "gallery file exceeds 25 MB guardrail" }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "gallery image has invalid dimensions" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "gallery image exceeds 20 megapixel guardrail" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("gallery image could not be decoded")
        val analysisSize = ImageSize(bitmap.width, bitmap.height)
        val rgba = ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
        bitmap.copyPixelsToBuffer(rgba)
        bitmap.recycle()
        GalleryImport(
            // The bounded decoded image is the only image registered with the native runtime,
            // so its dimensions are the valid reconstruction coordinate space.
            sourceSize = analysisSize,
            frame = PerceptionFrame(
                frameId = System.nanoTime(),
                timestampMillis = System.currentTimeMillis(),
                size = analysisSize,
                rotationDegrees = 0,
                pixelFormat = PerceptionPixelFormat.RGBA8,
                rowStrideBytes = analysisSize.width * 4,
                bytes = rgba.array(),
                source = "gallery",
            ),
        )
    } }

    /** Uses the same bounded RGBA path for an ImageCapture file before its pixels cross the JNI boundary. */
    suspend fun decodeFile(file: File): Result<GalleryImport> = withContext(Dispatchers.IO) { runCatching {
        require(file.isFile && file.length() in 1..MAX_FILE_BYTES) { "captured image exceeds 25 MB guardrail" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        FileInputStream(file).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "captured image has invalid dimensions" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= MAX_SOURCE_PIXELS) { "captured image exceeds 20 megapixel guardrail" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = FileInputStream(file).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("captured image could not be decoded")
        val analysisSize = ImageSize(bitmap.width, bitmap.height)
        val rgba = ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
        bitmap.copyPixelsToBuffer(rgba)
        bitmap.recycle()
        GalleryImport(
            sourceSize = analysisSize,
            frame = PerceptionFrame(
                frameId = System.nanoTime(),
                timestampMillis = System.currentTimeMillis(),
                size = analysisSize,
                rotationDegrees = 0,
                pixelFormat = PerceptionPixelFormat.RGBA8,
                rowStrideBytes = analysisSize.width * 4,
                bytes = rgba.array(),
                source = "camera_capture",
            ),
        )
    } }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample * (height / sample) > ANALYSIS_PIXELS) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_FILE_BYTES = 25L * 1024L * 1024L
        const val MAX_SOURCE_PIXELS = 20_000_000L
        const val ANALYSIS_PIXELS = 1_000_000
    }
}
