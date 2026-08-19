package dev.wellfriend.scan.ui.camera

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
                scaleType = PreviewView.ScaleType.FILL_CENTER
                if (enabled) controller.bind(this)
            }
        },
    )
}
