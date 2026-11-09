package dk.casp664c.streamkey

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Real stream keys are secrets. Block screenshots and preview thumbnails.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StreamKeyScreen()
            }
        }
    }
}
