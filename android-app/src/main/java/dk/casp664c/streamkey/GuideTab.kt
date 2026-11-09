package dk.casp664c.streamkey

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val STEPS = listOf(
    "Tjek at din konto må gå LIVE. TikTok bestemmer selv kravene (fx alder og følgere), og de kan ændre sig. " +
        "Se TikToks hjælpecenter.",
    "Hent serveradresse og streamnøgle fra TikToks egne værktøjer: LIVE Studio til computer, eller den " +
        "webudgave TikTok tilbyder din konto, når du vælger at gå LIVE.",
    "Nøglen er hemmelig, og TikTok kan give en ny hver gang. Bliver den afvist, så hent en ny.",
    "Indsæt server og nøgle i fanen Eksport, og gem dem som en profil i fanen Profiler.",
    "Tryk Test forbindelse. Det viser, om serveren svarer. Selve nøglen kontrolleres først, når du går live.",
    "Gå live fra fanen Stream, eller kopiér oplysningerne til en anden streamingapp."
)

private val TIKTOK_PACKAGES = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

@Composable
fun GuideTab(onContinue: () -> Unit) {
    val context = LocalContext.current
    val done = remember { mutableStateListOf(*Array(STEPS.size) { false }) }
    var status by remember { mutableStateOf("") }

    InfoCard(
        title = "Sådan får du din egen streamnøgle",
        body = "Appen kan ikke hente nøglen for dig. Den udstedes kun af TikTok til en konto, der har " +
            "adgang til LIVE. Følg trinnene, og sæt flueben undervejs."
    )

    STEPS.forEachIndexed { index, text ->
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(checked = done[index], onCheckedChange = { done[index] = it })
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Trin ${index + 1}", style = MaterialTheme.typography.labelLarge)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val launch = TIKTOK_PACKAGES.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            status = if (launch != null) {
                try {
                    context.startActivity(launch)
                    ""
                } catch (_: ActivityNotFoundException) {
                    "TikTok-appen kunne ikke åbnes."
                }
            } else {
                "TikTok-appen er ikke installeret her. Prøv i stedet at åbne den i browseren."
            }
        }) { Text("Åbn TikTok") }
        OutlinedButton(onClick = {
            status = try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/live")))
                ""
            } catch (_: ActivityNotFoundException) {
                "Ingen browser fundet."
            }
        }) { Text("Åbn i browser") }
    }
    if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.error)

    Text(
        "Del aldrig din streamnøgle med andre. Den giver adgang til at sende live på din konto.",
        style = MaterialTheme.typography.bodySmall
    )
    OutlinedButton(onClick = onContinue) { Text("Videre til Eksport") }
}
