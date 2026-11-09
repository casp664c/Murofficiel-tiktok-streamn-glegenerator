package dk.casp664c.streamkey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Plain RTMP sends the stream key unencrypted, so the UI warns when it is used. */
@Composable
fun PlainRtmpWarning(server: String) {
    if (server.trim().startsWith("rtmp://", ignoreCase = true)) {
        Text(
            "Adressen bruger rtmp:// uden kryptering. Streamnøglen sendes i klartekst. " +
                "Brug rtmps://, hvis serveren understøtter det.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

fun describe(result: RtmpProbeResult): String = when (result) {
    is RtmpProbeResult.Reachable ->
        "Serveren svarer som en RTMP-server (" + result.millis + " ms" +
            (if (result.secure) ", krypteret" else "") +
            "). Selve nøglen kontrolleres først, når du går live."
    is RtmpProbeResult.Failed -> result.message
}
