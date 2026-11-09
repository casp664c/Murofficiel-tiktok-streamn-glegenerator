package dk.casp664c.streamkey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StreamKeyScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { NotConfiguredTikTokProvider() }
    var page by rememberSaveable { mutableIntStateOf(0) }

    // Intentionally NOT saveable or persisted across process/Activity recreation.
    var server by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    var backend by rememberSaveable { mutableStateOf("") }
    var generatorStatus by remember { mutableStateOf("Ingen TikTok-session er oprettet.") }
    var copyStatus by remember { mutableStateOf("") }
    var backendStatus by remember { mutableStateOf("Endnu ikke testet.") }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("LIVE KEY STUDIO", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Android 0.1-dev • Egen generator og Altare-backend",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        TabRow(selectedTabIndex = page) {
            listOf("Generator", "Eksport", "Altare").forEachIndexed { index, label ->
                Tab(selected = page == index, onClick = { page = index },
                    text = { Text(label) })
            }
        }

        when (page) {
            0 -> {
                InfoCard(
                    title = "TikTok LIVE: ikke tilsluttet",
                    body = "Denne udgave har endnu ikke en verificeret uofficiel " +
                        "TikTok LIVE-integration. En rigtig nøgle skal udstedes og " +
                        "accepteres af TikTok. Appen opfinder ikke en nøgle."
                )
                Button(onClick = {
                    when (val result = provider.createSession()) {
                        is LiveRequestResult.Ready -> {
                            server = result.server
                            key = result.streamKey
                            generatorStatus = "En session blev modtaget. Se fanen Eksport."
                            page = 1
                        }
                        is LiveRequestResult.Unavailable -> generatorStatus = result.reason
                    }
                }) { Text("Kontrollér generator") }
                Text(generatorStatus, style = MaterialTheme.typography.bodyMedium)
                Text("Har du allerede en rigtig RTMP-adresse og nøgle, kan du " +
                    "bruge Eksport-fanen med andre streamingapps.")
            }

            1 -> {
                InfoCard(
                    title = "Brug i andre livestreamingapps",
                    body = "Indsæt kun en gyldig serveradresse og nøgle, du har ret " +
                        "til at bruge. Formatkontrol betyder ikke, at streamen virker."
                )
                OutlinedTextField(
                    value = server, onValueChange = { server = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("RTMP/RTMPS-server") },
                    placeholder = { Text("rtmps://live.example.com/app") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Streamnøgle") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "Skjul" else "Vis")
                        }
                    }
                )
                if (server.isNotEmpty() && !StreamSettings.validServer(server)) {
                    Text("Serveren skal begynde med rtmp:// eller rtmps:// og have et værtsnavn.",
                        color = MaterialTheme.colorScheme.error)
                }
                val valid = StreamSettings.validServer(server) && StreamSettings.validKey(key)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        copy(context, "RTMP-server", server.trim())
                        copyStatus = "Serveradresse kopieret."
                    }, enabled = StreamSettings.validServer(server)) {
                        Text("Kopiér server")
                    }
                    OutlinedButton(onClick = {
                        copy(context, "Streamnøgle", key.trim())
                        copyStatus = "Streamnøgle kopieret til udklipsholderen."
                    }, enabled = StreamSettings.validKey(key)) {
                        Text("Kopiér nøgle")
                    }
                }
                Button(onClick = {
                    val combined = StreamSettings.combinedUrl(server, key)
                    if (combined != null) {
                        copy(context, "Samlet streamingadresse", combined)
                        copyStatus = "Samlet streamingadresse kopieret."
                    }
                }, enabled = StreamSettings.combinedUrl(server, key) != null) {
                    Text("Kopiér samlet adresse")
                }
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT,
                            "RTMP/RTMPS-server: " + server.trim() +
                                "\nStreamnøgle: " + key.trim())
                    }
                    context.startActivity(Intent.createChooser(intent,
                        "Del streamingoplysninger med en app"))
                    copyStatus = "Du vælger selv, hvilken app der må modtage oplysningerne."
                }, enabled = valid) {
                    Text("Del med anden app")
                }
                Text("Advarsel: Streamnøgler er hemmelige. Andre apps og " +
                    "udklipsholderen kan gemme dem. Del kun med apps, du stoler på.",
                    style = MaterialTheme.typography.bodySmall)
                if (copyStatus.isNotBlank()) Text(copyStatus)
            }

            2 -> {
                InfoCard(
                    title = "Altare / egen backend",
                    body = "Indtast din HTTPS-adresse til backend, når den er " +
                        "installeret. Appen sender aldrig streamnøgler til health-checket."
                )
                OutlinedTextField(
                    value = backend, onValueChange = { backend = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("HTTPS-backend") },
                    placeholder = { Text("https://din-server.example") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true
                )
                Button(
                    onClick = {
                        backendStatus = "Tjekker forbindelse..."
                        scope.launch {
                            backendStatus = withContext(Dispatchers.IO) {
                                BackendProbe.check(backend)
                            }
                        }
                    },
                    enabled = BackendProbe.isValidHttpsBase(backend)
                ) { Text("Test serverforbindelse") }
                Text(backendStatus)
                Text("Altare er ikke blevet installeret eller tilsluttet automatisk.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun copy(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
