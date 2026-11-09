package dk.casp664c.streamkey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfilesTab(
    repo: ProfileRepository,
    server: String,
    key: String,
    onUse: (StreamProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(repo.list()) }
    var name by remember { mutableStateOf("") }
    var includeKey by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<StreamProfile?>(null) }

    InfoCard(
        title = "Gemte profiler",
        body = "Gem server og nøgle på telefonen. Nøglen krypteres med en nøgle i Android Keystore " +
            "og forlader aldrig telefonen. Profiler flyttes ikke med en sikkerhedskopi eller til en ny telefon."
    )

    OutlinedTextField(
        value = name, onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Navn på profil") },
        placeholder = { Text("Fx Hjemme-stream") },
        singleLine = true
    )
    Text(
        if (server.isBlank()) "Udfyld serveren i fanen Eksport først."
        else "Gemmer: $server" + if (key.isNotBlank() && includeKey) " + nøgle" else "",
        style = MaterialTheme.typography.bodySmall
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = includeKey, onCheckedChange = { includeKey = it })
        Text("Gem også streamnøglen (krypteret)")
    }
    Button(
        onClick = {
            status = try {
                val saved = repo.save(name, server, if (includeKey) key else "")
                profiles = repo.list()
                name = ""
                "Profilen \"" + saved.name + "\" er gemt."
            } catch (e: IllegalArgumentException) {
                e.message ?: "Profilen kunne ikke gemmes."
            }
        },
        enabled = name.isNotBlank() && server.isNotBlank()
    ) { Text("Gem som profil") }
    if (status.isNotBlank()) Text(status)

    Text("Dine profiler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (profiles.isEmpty()) {
        Text("Ingen gemte profiler endnu.", style = MaterialTheme.typography.bodyMedium)
    }
    profiles.forEach { profile ->
        var probe by remember(profile.id) { mutableStateOf("") }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(profile.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(profile.server, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (profile.key.isNotBlank()) "Nøgle gemt (krypteret)" else "Ingen nøgle gemt",
                    style = MaterialTheme.typography.bodySmall
                )
                PlainRtmpWarning(profile.server)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onUse(profile) }) { Text("Brug") }
                    OutlinedButton(onClick = {
                        probe = "Tester..."
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { RtmpProbe.check(profile.server) }
                            probe = describe(result)
                        }
                    }) { Text("Test") }
                    TextButton(onClick = { pendingDelete = profile }) { Text("Slet") }
                }
                if (probe.isNotBlank()) Text(probe, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Slet profil?") },
            text = { Text("\"" + profile.name + "\" og den gemte nøgle slettes fra telefonen.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.delete(profile.id)
                    profiles = repo.list()
                    pendingDelete = null
                }) { Text("Slet") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Annuller") }
            }
        )
    }
}
