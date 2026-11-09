package dk.casp664c.streamkey

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pedro.library.view.OpenGlView

private fun hasStreamingPermissions(context: Context): Boolean =
    listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun BroadcastTab(server: String, key: String) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasStreamingPermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasStreamingPermissions(context) }

    var broadcaster by remember { mutableStateOf<Broadcaster?>(null) }
    var live by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Klar. Tryk Gå LIVE, når du har tjekket server og nøgle.") }
    val url = StreamSettings.combinedUrl(server, key)

    InfoCard(
        title = "Stream direkte fra telefonen",
        body = "Kamera og mikrofon sendes som RTMP til den server og nøgle, du har indtastet. " +
            "Streamen stopper, hvis du forlader appen. Hold skærmen tændt, mens du er live."
    )

    if (!granted) {
        Text("Appen skal have lov til at bruge kamera og mikrofon.")
        Button(onClick = {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }) { Text("Giv adgang til kamera og mikrofon") }
        return
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(380.dp),
        factory = { ctx ->
            OpenGlView(ctx).also { view ->
                view.keepScreenOn = true
                broadcaster = Broadcaster(view) { text, isLive ->
                    status = text
                    live = isLive
                }
            }
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            broadcaster?.release()
            broadcaster = null
        }
    }

    PlainRtmpWarning(server)
    if (url == null) {
        Text(
            "Udfyld en gyldig server og nøgle i fanen Eksport (eller vælg en profil). " +
                "En server med ? i adressen understøttes ikke her.",
            style = MaterialTheme.typography.bodySmall
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!live) {
            Button(
                onClick = {
                    if (url != null) {
                        status = "Forbinder..."
                        broadcaster?.start(url)
                    }
                },
                enabled = url != null && broadcaster != null
            ) { Text("Gå LIVE") }
        } else {
            Button(onClick = { broadcaster?.stop() }) { Text("Stop stream") }
        }
        OutlinedButton(onClick = { broadcaster?.switchCamera() }, enabled = broadcaster != null) {
            Text("Skift kamera")
        }
    }
    Text(status, style = MaterialTheme.typography.bodyMedium)
}
