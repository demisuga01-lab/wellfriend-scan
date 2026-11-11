package dev.wellfriend.scan.perception

import dev.wellfriend.scan.core.ImageSize

/**
 * Bounded, app-owned decoded-pixel cache for the native scalar runtime.
 * It is deliberately not a decoder and never reads arbitrary paths: Android UI code explicitly
 * registers already guarded gallery/camera pixels before reconstruction or filtering.
 */
internal const val MAX_NATIVE_RUNTIME_IMAGE_BYTES = 16 * 1024 * 1024

data class NativeRuntimeImage(
    val width: Int,
    val height: Int,
    val stride: Int,
    val pixelFormat: PerceptionPixelFormat,
    val bytes: ByteArray,
) {
    init {
        require(width > 0 && height > 0 && stride >= width) { "invalid runtime image dimensions" }
        require(pixelFormat != PerceptionPixelFormat.YUV420) { "native scalar runtime requires converted pixels" }
        require(bytes.size <= MAX_NATIVE_RUNTIME_IMAGE_BYTES) { "runtime image exceeds 16 MiB mobile cache limit" }
        require(stride.toLong() * height <= bytes.size) { "runtime image bytes do not match stride and dimensions" }
    }

    fun size() = ImageSize(width, height)

    companion object {
        fun fromFrame(frame: PerceptionFrame): NativeRuntimeImage = NativeRuntimeImage(
            frame.size.width,
            frame.size.height,
            frame.rowStrideBytes,
            frame.pixelFormat,
            frame.bytes,
        )
    }
}

/** Keeps original and derived scalar images private to the native bridge; session state stores URIs only. */
class NativeRuntimeImageStore {
    private val images = LinkedHashMap<String, NativeRuntimeImage>()
    private var outputId = 0L

    @Synchronized fun register(uri: String, image: NativeRuntimeImage) {
        require(uri.isNotBlank()) { "runtime image URI must not be blank" }
        images[uri] = image
    }

    @Synchronized fun resolve(uri: String, expectedSize: ImageSize? = null): NativeRuntimeImage {
        val image = images[uri]
            ?: throw NativePerceptionUnavailableException("decoded source pixels are not registered for $uri; no path decoder fallback is allowed")
        if (expectedSize != null && (image.width != expectedSize.width || image.height != expectedSize.height)) {
            throw NativePerceptionUnavailableException("runtime source image dimensions do not match the page request")
        }
        return image
    }

    @Synchronized fun registerOutput(image: NativeRuntimeImage): String {
        val uri = "wellfriend-runtime://native/${++outputId}"
        images[uri] = image
        return uri
    }
}
