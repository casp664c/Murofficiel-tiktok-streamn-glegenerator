package dk.casp664c.streamkey

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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

private val TABS = listOf("Generator", "Profiler", "Eksport", "Guide", "Stream", "Altare")

@Composable
fun StreamKeyScreen() {
    val context = LocalContext.current
    val cipher = remember { KeystoreCipher() }
    val repo = remember {
        ProfileRepository(PrefsStore(context.getSharedPreferences("profiles", Context.MODE_PRIVATE)), cipher)
    }
    val accounts = remember {
        AccountStore(PrefsStore(context.getSharedPreferences("account", Context.MODE_PRIVATE)), cipher)
    }
    var page by rememberSaveable { mutableIntStateOf(0) }

    // The live server/key being edited are intentionally NOT saveable: they only survive
    // in memory. Persisted copies exist only as explicitly saved, encrypted profiles.
    var server by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "LIVE KEY STUDIO", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Android 0.2-dev • Profiler, forbindelsestest og egen Altare-backend",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ScrollableTabRow(selectedTabIndex = page, edgePadding = 0.dp) {
            TABS.forEachIndexed { index, label ->
                Tab(selected = page == index, onClick = { page = index }, text = { Text(label) })
            }
        }

        when (page) {
            0 -> GeneratorTab(onReady = { s, k -> server = s; key = k; page = 2 })
            1 -> ProfilesTab(
                repo = repo, server = server, key = key,
                onUse = { profile -> server = profile.server; key = profile.key; page = 2 }
            )
            2 -> ExportTab(server, key, onServer = { server = it }, onKey = { key = it })
            3 -> GuideTab(onContinue = { page = 2 })
            4 -> BroadcastTab(server = server, key = key)
            else -> AltareTab(
                accounts = accounts, server = server, key = key,
                onUse = { profile -> server = profile.server; key = profile.key; page = 2 }
            )
        }
    }
}

@Composable
private fun GeneratorTab(onReady: (String, String) -> Unit) {
    val provider = remember { NotConfiguredTikTokProvider() }
    var status by remember { mutableStateOf("Ingen TikTok-session er oprettet.") }
    InfoCard(
        title = "TikTok LIVE: ikke tilsluttet",
        body = "TikTok har ingen offentlig API til at udstede streamnøgler, og appen opfinder " +
            "aldrig en nøgle. Hent din egen server og nøgle fra TikToks officielle værktøjer. " +
            "Fanen Guide viser hvordan."
    )
    Button(onClick = {
        when (val result = provider.createSession()) {
            is LiveRequestResult.Ready -> {
                status = "En session blev modtaget."
                onReady(result.server, result.streamKey)
            }
            is LiveRequestResult.Unavailable -> status = result.reason
        }
    }) { Text("Kontrollér generator") }
    Text(status, style = MaterialTheme.typography.bodyMedium)
    Text("Har du allerede en RTMP-adresse og nøgle, kan du bruge Profiler og Eksport.")
}

@Composable
private fun ExportTab(server: String, key: String, onServer: (String) -> Unit, onKey: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showKey by remember { mutableStateOf(false) }
    var copyStatus by remember { mutableStateOf("") }
    var probeStatus by remember { mutableStateOf("") }

    InfoCard(
        title = "Brug i andre livestreamingapps",
        body = "Indsæt kun en serveradresse og nøgle, du har ret til at bruge. " +
            "Formatkontrol betyder ikke, at streamen virker."
    )
    OutlinedTextField(
        value = server, onValueChange = onServer,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("RTMP/RTMPS-server") },
        placeholder = { Text("rtmps://live.example.com/app") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true
    )
    OutlinedTextField(
        value = key, onValueChange = onKey,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Streamnøgle") },
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Skjul" else "Vis") }
        }
    )
    if (server.isNotEmpty() && !StreamSettings.validServer(server)) {
        Text(
            "Serveren skal begynde med rtmp:// eller rtmps:// og have et værtsnavn.",
            color = MaterialTheme.colorScheme.error
        )
    }
    PlainRtmpWarning(server)

    val valid = StreamSettings.validServer(server) && StreamSettings.validKey(key)
    OutlinedButton(
        onClick = {
            probeStatus = "Tester forbindelse..."
            scope.launch {
                val result = withContext(Dispatchers.IO) { RtmpProbe.check(server) }
                probeStatus = describe(result)
            }
        },
        enabled = StreamSettings.validServer(server)
    ) { Text("Test forbindelse") }
    if (probeStatus.isNotBlank()) Text(probeStatus, style = MaterialTheme.typography.bodyMedium)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                copyToClipboard(context, "RTMP-server", server.trim())
                copyStatus = "Serveradresse kopieret."
            },
            enabled = StreamSettings.validServer(server)
        ) { Text("Kopiér server") }
        OutlinedButton(
            onClick = {
                copyToClipboard(context, "Streamnøgle", key.trim())
                copyStatus = "Streamnøgle kopieret til udklipsholderen."
            },
            enabled = StreamSettings.validKey(key)
        ) { Text("Kopiér nøgle") }
    }
    Button(
        onClick = {
            val combined = StreamSettings.combinedUrl(server, key)
            if (combined != null) {
                copyToClipboard(context, "Samlet streamingadresse", combined)
                copyStatus = "Samlet streamingadresse kopieret."
            }
        },
        enabled = StreamSettings.combinedUrl(server, key) != null
    ) { Text("Kopiér samlet adresse") }
    OutlinedButton(
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "RTMP/RTMPS-server: " + server.trim() + "\nStreamnøgle: " + key.trim()
                )
            }
            context.startActivity(Intent.createChooser(intent, "Del streamingoplysninger med en app"))
            copyStatus = "Du vælger selv, hvilken app der må modtage oplysningerne."
        },
        enabled = valid
    ) { Text("Del med anden app") }
    Text(
        "Advarsel: Streamnøgler er hemmelige. Andre apps og udklipsholderen kan gemme dem. " +
            "Del kun med apps, du stoler på.",
        style = MaterialTheme.typography.bodySmall
    )
    if (copyStatus.isNotBlank()) Text(copyStatus)
}
