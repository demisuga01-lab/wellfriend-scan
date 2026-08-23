package dev.wellfriend.scan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.core.content.FileProvider
import dev.wellfriend.scan.core.ScanPage
import dev.wellfriend.scan.export.ExportFormat
import dev.wellfriend.scan.perception.NativeRuntimeImageStore
import dev.wellfriend.scan.ui.RuntimeImageBitmap
import java.io.File
import java.io.FileOutputStream

/** Writes only a registered, processed native runtime image and shares it through a FileProvider. */
class AndroidProcessedPageExporter(
    private val context: Context,
    private val runtimeImages: NativeRuntimeImageStore,
) {
    fun exportAndShare(page: ScanPage, format: ExportFormat): Result<File> = runCatching {
        require(format == ExportFormat.JPEG || format == ExportFormat.PNG) { "only JPEG and PNG are available" }
        val uri = page.displayPage?.uri ?: page.sourceUri
        val image = runtimeImages.resolve(uri)
        val extension = if (format == ExportFormat.PNG) "png" else "jpg"
        val output = File(context.cacheDir, "shared/${page.id}.$extension").also { it.parentFile?.mkdirs() }
        val source = RuntimeImageBitmap.decode(image)
        val bitmap = if (page.rotationDegrees == 0) source else Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }, true,
        )
        val compressed = FileOutputStream(output).use { stream ->
            bitmap.compress(
                if (format == ExportFormat.PNG) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG,
                92,
                stream,
            )
        }
        require(compressed) { "processed image could not be encoded" }
        val shared = FileProvider.getUriForFile(context, "${context.packageName}.files", output)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType(if (format == ExportFormat.PNG) "image/png" else "image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, shared)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share scanned page",
            ),
        )
        output
    }
}
