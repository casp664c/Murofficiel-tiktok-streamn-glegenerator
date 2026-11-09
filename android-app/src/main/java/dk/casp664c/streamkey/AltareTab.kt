package dk.casp664c.streamkey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AltareTab(
    accounts: AccountStore,
    server: String,
    key: String,
    onUse: (StreamProfile) -> Unit
) {
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf(accounts.load()) }
    var backend by rememberSaveable { mutableStateOf(account?.baseUrl.orEmpty()) }
    var backendStatus by remember { mutableStateOf("Endnu ikke testet.") }

    // Passwords are held only in these fields while typing and are never saved.
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var authStatus by remember { mutableStateOf("") }

    var profileName by remember { mutableStateOf("") }
    var uploadKey by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<List<RemoteProfile>>(emptyList()) }
    var syncStatus by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    InfoCard(
        title = "Altare / egen backend",
        body = "Indtast HTTPS-adressen til din egen backend. Med en konto kan du gemme profiler på " +
            "serveren og hente dem på en anden telefon. Appen sender aldrig dit TikTok-login, " +
            "og nøglen sendes kun, hvis du selv vælger det."
    )
    OutlinedTextField(
        value = backend, onValueChange = { backend = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("HTTPS-backend") },
        placeholder = { Text("https://din-server.example") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        singleLine = true
    )
    OutlinedButton(
        onClick = {
            backendStatus = "Tjekker forbindelse..."
            scope.launch { backendStatus = withContext(Dispatchers.IO) { BackendProbe.check(backend) } }
        },
        enabled = BackendProbe.isValidHttpsBase(backend)
    ) { Text("Test serverforbindelse") }
    Text(backendStatus)

    val current = account
    if (current == null) {
        Text("Konto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = username, onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Brugernavn") }, singleLine = true
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Adgangskode (mindst 10 tegn)") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            value = inviteCode, onValueChange = { inviteCode = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Invitationskode (hvis serveren kræver det)") }, singleLine = true
        )
        val canAuth = BackendProbe.isValidHttpsBase(backend) && username.isNotBlank() && password.isNotEmpty() && !busy
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                busy = true
                authStatus = "Logger ind..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) { BackendClient(backend).login(username, password) }
                    busy = false
                    when (result) {
                        is ApiResult.Ok -> {
                            val saved = SavedAccount(backend.trim().trimEnd('/'), result.value.username, result.value.token)
                            accounts.save(saved)
                            account = saved
                            password = ""
                            authStatus = ""
                        }
                        is ApiResult.Err -> authStatus = result.message
                    }
                }
            }, enabled = canAuth) { Text("Log ind") }
            OutlinedButton(onClick = {
                busy = true
                authStatus = "Opretter konto..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        BackendClient(backend).register(username, password, inviteCode)
                    }
                    busy = false
                    when (result) {
                        is ApiResult.Ok -> {
                            val saved = SavedAccount(backend.trim().trimEnd('/'), result.value.username, result.value.token)
                            accounts.save(saved)
                            account = saved
                            password = ""
                            inviteCode = ""
                            authStatus = ""
                        }
                        is ApiResult.Err -> authStatus = result.message
                    }
                }
            }, enabled = canAuth) { Text("Opret konto") }
        }
        if (authStatus.isNotBlank()) Text(authStatus)
    } else {
        Text(
            "Logget ind som ${current.username}",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
        )
        Text(current.baseUrl, style = MaterialTheme.typography.bodySmall)

        // A 401 means the token expired or was revoked: drop it and ask for a new login.
        fun handleError(message: String) {
            syncStatus = message
            if (message.startsWith("Din session")) {
                accounts.clear()
                account = null
                remote = emptyList()
            }
        }

        OutlinedTextField(
            value = profileName, onValueChange = { profileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Navn til profil på serveren") }, singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = uploadKey, onCheckedChange = { uploadKey = it })
            Text("Send også streamnøglen (krypteres på serveren)")
        }
        Text(
            "Serveren gemmer kun nøglen, hvis administratoren har slået nøglelagring til. " +
                "Du kan altid vælge kun at gemme serveradressen.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = {
            busy = true
            syncStatus = "Gemmer på serveren..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    BackendClient(current.baseUrl).createProfile(
                        current.token, profileName, server, if (uploadKey) key else null
                    )
                }
                busy = false
                when (result) {
                    is ApiResult.Ok -> {
                        syncStatus = "Profilen blev gemt på serveren."
                        profileName = ""
                    }
                    is ApiResult.Err -> handleError(result.message)
                }
            }
        }, enabled = profileName.isNotBlank() && StreamSettings.validServer(server) && !busy) {
            Text("Gem aktiv profil på server")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                busy = true
                syncStatus = "Henter profiler..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) { BackendClient(current.baseUrl).listProfiles(current.token) }
                    busy = false
                    when (result) {
                        is ApiResult.Ok -> {
                            remote = result.value
                            syncStatus = if (result.value.isEmpty()) "Ingen profiler på serveren." else ""
                        }
                        is ApiResult.Err -> handleError(result.message)
                    }
                }
            }, enabled = !busy) { Text("Hent profiler") }
            TextButton(onClick = {
                val token = current.token
                val url = current.baseUrl
                accounts.clear()
                account = null
                remote = emptyList()
                syncStatus = ""
                scope.launch { withContext(Dispatchers.IO) { BackendClient(url).logout(token) } }
            }) { Text("Log ud") }
        }
        if (syncStatus.isNotBlank()) Text(syncStatus)

        remote.forEach { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(profile.server, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (profile.hasKey) "Nøgle gemt på serveren" else "Ingen nøgle på serveren",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                if (!profile.hasKey) {
                                    onUse(StreamProfile(profile.id, profile.name, profile.server, ""))
                                    return@launch
                                }
                                val result = withContext(Dispatchers.IO) {
                                    BackendClient(current.baseUrl).revealKey(current.token, profile.id)
                                }
                                when (result) {
                                    is ApiResult.Ok -> onUse(
                                        StreamProfile(profile.id, profile.name, result.value.server, result.value.key)
                                    )
                                    is ApiResult.Err -> handleError(result.message)
                                }
                            }
                        }) { Text("Brug") }
                        TextButton(onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    BackendClient(current.baseUrl).deleteProfile(current.token, profile.id)
                                }
                                when (result) {
                                    is ApiResult.Ok -> remote = remote.filterNot { it.id == profile.id }
                                    is ApiResult.Err -> handleError(result.message)
                                }
                            }
                        }) { Text("Slet fra server") }
                    }
                }
            }
        }
    }
    Text(
        "Altare er ikke blevet installeret eller tilsluttet automatisk.",
        style = MaterialTheme.typography.bodySmall
    )
}
