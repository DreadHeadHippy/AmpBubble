package com.plexbubble.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.plexbubble.app.data.SecureTokenStore
import com.plexbubble.app.data.SettingsStore
import com.plexbubble.app.overlay.BubbleOverlayService
import com.plexbubble.app.plex.PlexAuthRepository
import com.plexbubble.app.plex.PlexServerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var tokenStore: SecureTokenStore

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsStore = SettingsStore(applicationContext)
        tokenStore = SecureTokenStore(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme {
                Surface {
                    MainScreen(
                        settingsStore = settingsStore,
                        tokenStore = tokenStore,
                        canDrawOverlays = { Settings.canDrawOverlays(this) },
                        openOverlaySettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        isNotificationAccessGranted = { isNotificationListenerEnabled() },
                        openNotificationAccessSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        openAuthTab = { url -> openCustomTab(url) },
                        onToggleBubble = { enabled -> toggleBubbleService(enabled) }
                    )
                }
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: ""
        return enabled.contains(packageName)
    }

    private fun openCustomTab(url: String) {
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
    }

    private fun toggleBubbleService(enabled: Boolean) {
        val intent = Intent(this, BubbleOverlayService::class.java)
        if (enabled) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            intent.action = BubbleOverlayService.ACTION_STOP
            ContextCompat.startForegroundService(this, intent)
        }
    }
}

@Composable
private fun MainScreen(
    settingsStore: SettingsStore,
    tokenStore: SecureTokenStore,
    canDrawOverlays: () -> Boolean,
    openOverlaySettings: () -> Unit,
    isNotificationAccessGranted: () -> Boolean,
    openNotificationAccessSettings: () -> Unit,
    openAuthTab: (String) -> Unit,
    onToggleBubble: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clientIdentifier = remember { mutableStateOf("") }
    var signInStatus by remember { mutableStateOf("Not signed in") }
    var manualBaseUrl by remember { mutableStateOf("") }
    var manualToken by remember { mutableStateOf("") }
    var useManualOverride by remember { mutableStateOf(false) }
    var bubbleEnabled by remember { mutableStateOf(false) }
    var ratingPresetsCsv by remember { mutableStateOf("") }
    var showRecentInBubble by remember { mutableStateOf(false) }
    var diagnosticsSummary by remember { mutableStateOf("No diagnostics yet") }
    var snapshotText by remember { mutableStateOf("") }
    var importStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        clientIdentifier.value = settingsStore.getOrCreateClientIdentifier()
        if (!tokenStore.authToken.isNullOrBlank()) signInStatus = "Signed in"
        useManualOverride = settingsStore.useManualServer.first()
        manualBaseUrl = settingsStore.manualBaseUrl.first()
        bubbleEnabled = settingsStore.bubbleEnabled.first()
        ratingPresetsCsv = settingsStore.getRatingPresets().joinToString(",")
        showRecentInBubble = settingsStore.showRecentInBubble.first()
        diagnosticsSummary = settingsStore.getDiagnosticEvents().takeLast(8).joinToString("\n")
    }

    val panelShape = RoundedCornerShape(20.dp)
    val panelColors = CardDefaults.cardColors(containerColor = Color(0xE617181D))
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFFE5A00D),
        unfocusedBorderColor = Color(0x55FFFFFF),
        focusedTextColor = Color(0xFFF2F4F8),
        unfocusedTextColor = Color(0xFFF2F4F8),
        focusedLabelColor = Color(0xFFE5A00D),
        unfocusedLabelColor = Color(0xFFBCC2CF),
        cursorColor = Color(0xFFE5A00D)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E0F12), Color(0xFF171920), Color(0xFF0B0C0E))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(shape = panelShape, colors = panelColors) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0x22E5A00D), Color(0x00000000)))
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "PlexBubble",
                        color = Color(0xFFF5F7FB),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Premium quick setup for always-on-track rating",
                        color = Color(0xFFBCC2CF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Permissions", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                    Text("Overlay + notification access", color = Color(0xFFBCC2CF), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = openOverlaySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color(0xFF151515))
                    ) {
                        Text(if (canDrawOverlays()) "Overlay granted" else "Grant overlay")
                    }
                    Button(
                        onClick = openNotificationAccessSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2C35), contentColor = Color(0xFFE9ECF4))
                    ) {
                        Text(if (isNotificationAccessGranted()) "Notification access granted" else "Grant notification access")
                    }
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Plex account", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                    Text(signInStatus, color = Color(0xFFD6DAE5), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = {
                            scope.launch {
                                val authRepo = PlexAuthRepository(clientIdentifier.value)
                                val created = authRepo.createPin()
                                created.onSuccess { (pin, authUrl) ->
                                    openAuthTab(authUrl)
                                    signInStatus = "Waiting for browser sign-in..."
                                    val tokenResult = authRepo.pollForToken(pin)
                                    tokenResult.onSuccess { token ->
                                        tokenStore.authToken = token
                                        signInStatus = "Signed in, discovering server..."
                                        val serverRepo = PlexServerRepository(clientIdentifier.value)
                                        val servers = serverRepo.listOwnedServers(token).getOrNull().orEmpty()
                                        val owned = servers.firstOrNull { it.owned }
                                        val resolved = owned?.let { serverRepo.resolveReachableBaseUrl(it, token) }
                                        if (resolved != null) {
                                            settingsStore.setResolvedBaseUrl(resolved)
                                            signInStatus = "Signed in - server found"
                                        } else {
                                            signInStatus = "Signed in - no reachable server, use manual override below"
                                        }
                                    }.onFailure {
                                        signInStatus = "Sign-in failed: ${it.userFacingMessage()}"
                                    }
                                }.onFailure {
                                    signInStatus = "Could not start sign-in: ${it.userFacingMessage()}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color(0xFF151515))
                    ) {
                        Text("Sign in with Plex")
                    }
                    Button(
                        onClick = {
                            tokenStore.clear()
                            signInStatus = "Not signed in"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1215), contentColor = Color(0xFFFFCFD4))
                    ) {
                        Text("Sign out")
                    }
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Manual server override", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x33E5A00D), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(checked = useManualOverride, onCheckedChange = { useManualOverride = it })
                        Text(text = "Use manual URL + token", color = Color(0xFFD6DAE5))
                    }
                    OutlinedTextField(
                        value = manualBaseUrl,
                        onValueChange = { manualBaseUrl = it },
                        label = { Text("Server base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )
                    OutlinedTextField(
                        value = manualToken,
                        onValueChange = { manualToken = it },
                        label = { Text("X-Plex-Token") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                settingsStore.setManualServer(useManualOverride, manualBaseUrl)
                                if (manualToken.isNotBlank()) tokenStore.authToken = manualToken
                                signInStatus = "Manual settings saved"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color(0xFF151515))
                    ) {
                        Text("Save manual settings")
                    }
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Bubble", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (bubbleEnabled) "Enabled" else "Disabled",
                            color = if (bubbleEnabled) Color(0xFF9BE2B6) else Color(0xFFD6DAE5)
                        )
                    }
                    Switch(checked = bubbleEnabled, onCheckedChange = { checked ->
                        bubbleEnabled = checked
                        onToggleBubble(checked)
                        scope.launch { settingsStore.setBubbleEnabled(checked) }
                    })
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reliability controls", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                    Text("Rating presets (advanced)", color = Color(0xFFBCC2CF), style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x33E5A00D), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show recent ratings in bubble (advanced)", color = Color(0xFFD6DAE5))
                        Switch(
                            checked = showRecentInBubble,
                            onCheckedChange = { checked ->
                                showRecentInBubble = checked
                                scope.launch { settingsStore.setShowRecentInBubble(checked) }
                            }
                        )
                    }
                    OutlinedTextField(
                        value = ratingPresetsCsv,
                        onValueChange = { ratingPresetsCsv = it },
                        label = { Text("Comma list, e.g. 2.5,3,4,5") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                settingsStore.setRatingPresetsFromText(ratingPresetsCsv)
                                ratingPresetsCsv = settingsStore.getRatingPresets().joinToString(",")
                                importStatus = "Preset values saved"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2C35), contentColor = Color(0xFFE9ECF4))
                    ) {
                        Text("Save presets")
                    }
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Diagnostics", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = diagnosticsSummary,
                        color = Color(0xFFD6DAE5),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                diagnosticsSummary = settingsStore.getDiagnosticEvents().takeLast(8).joinToString("\n")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2C35), contentColor = Color(0xFFE9ECF4))
                    ) {
                        Text("Refresh diagnostics")
                    }
                }
            }

            Card(shape = panelShape, colors = panelColors) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup / Restore", color = Color(0xFFF5F7FB), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = snapshotText,
                        onValueChange = { snapshotText = it },
                        label = { Text("Settings snapshot JSON") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    snapshotText = settingsStore.exportSettingsSnapshot()
                                    importStatus = "Snapshot exported"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2C35), contentColor = Color(0xFFE9ECF4))
                        ) {
                            Text("Export")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = settingsStore.importSettingsSnapshot(snapshotText)
                                    importStatus = if (result.isSuccess) "Snapshot imported" else "Import failed: ${result.exceptionOrNull()?.message ?: "unknown"}"
                                    diagnosticsSummary = settingsStore.getDiagnosticEvents().takeLast(8).joinToString("\n")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D), contentColor = Color(0xFF151515))
                        ) {
                            Text("Import")
                        }
                    }
                    if (importStatus != null) {
                        Text(importStatus!!, color = Color(0xFF9BE2B6), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun Throwable.userFacingMessage(): String {
    val message = message?.takeIf { it.isNotBlank() }
        ?: localizedMessage?.takeIf { it.isNotBlank() }
        ?: cause?.message?.takeIf { it.isNotBlank() }
    return message ?: this::class.java.simpleName
}
