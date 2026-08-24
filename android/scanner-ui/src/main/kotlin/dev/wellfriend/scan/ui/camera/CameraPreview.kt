package dev.wellfriend.scan.ui.camera

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(controller: CameraXScannerController, enabled: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.pointerInput(enabled) {
            if (enabled) detectTapGestures { offset -> controller.focusAt(offset.x, offset.y) }
        },
        factory = { context ->
            PreviewView(context).apply {
                // TextureView-backed preview avoids device-specific SurfaceView black frames.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                Log.i("WellfriendPreview", "PreviewView created implementation=COMPATIBLE scale=FILL_CENTER")
                controller.onPreviewViewCreated(this)
            }
        },
        // `factory` only runs once. Binding here is required after the asynchronous permission
        // grant causes the Compose state to change from REQUESTING_PERMISSION to CAMERA_STARTING.
        update = { previewView ->
            if (enabled) controller.bind(previewView) else controller.unbind(previewView)
        },
        onRelease = { previewView -> controller.unbind(previewView) },
    )
}
