package dev.wellfriend.scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Product shell only: CameraX analysis and Rust/JNI perception bindings are MP2 work. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WellfriendScanScreen() }
    }
}

@Composable
private fun WellfriendScanScreen() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Wellfriend Scan")
            Text("Camera preview and live document overlay are contract placeholders.")
            Button(onClick = {}) { Text("Manual capture") }
            Button(onClick = {}) { Text("Import from gallery") }
        }
    }
}

