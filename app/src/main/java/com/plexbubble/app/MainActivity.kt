package com.plexbubble.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
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

    LaunchedEffect(Unit) {
        clientIdentifier.value = settingsStore.getOrCreateClientIdentifier()
        if (!tokenStore.authToken.isNullOrBlank()) signInStatus = "Signed in"
        useManualOverride = settingsStore.useManualServer.first()
        manualBaseUrl = settingsStore.manualBaseUrl.first()
        bubbleEnabled = settingsStore.bubbleEnabled.first()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "PlexBubble", style = MaterialTheme.typography.headlineSmall)
        Text(text = "1. Overlay permission")
        Button(onClick = openOverlaySettings) {
            Text(if (canDrawOverlays()) "Overlay permission granted" else "Grant overlay permission")
        }

        Text(text = "2. Notification access (to detect the playing track)")
        Button(onClick = openNotificationAccessSettings) {
            Text(if (isNotificationAccessGranted()) "Notification access granted" else "Grant notification access")
        }

        Divider()
        Text(text = "3. Sign in to Plex")
        Text(text = signInStatus)
        Button(onClick = {
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
                        signInStatus = "Sign-in failed: ${it.message}"
                    }
                }.onFailure {
                    signInStatus = "Could not start sign-in: ${it.message}"
                }
            }
        }) {
            Text("Sign in with Plex")
        }
        Button(onClick = {
            tokenStore.clear()
            signInStatus = "Not signed in"
        }) {
            Text("Sign out")
        }

        Divider()
        Text(text = "Advanced: manual server override")
        androidx.compose.material3.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = useManualOverride, onCheckedChange = { useManualOverride = it })
            Text(text = "Use manual server URL + token")
        }
        OutlinedTextField(
            value = manualBaseUrl,
            onValueChange = { manualBaseUrl = it },
            label = { Text("Server base URL (e.g. http://192.168.1.10:32400)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = manualToken,
            onValueChange = { manualToken = it },
            label = { Text("X-Plex-Token") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            scope.launch {
                settingsStore.setManualServer(useManualOverride, manualBaseUrl)
                if (manualToken.isNotBlank()) tokenStore.authToken = manualToken
                signInStatus = "Manual settings saved"
            }
        }) {
            Text("Save manual settings")
        }

        Divider()
        Text(text = "4. Bubble")
        androidx.compose.material3.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = bubbleEnabled, onCheckedChange = { checked ->
                bubbleEnabled = checked
                onToggleBubble(checked)
                scope.launch { settingsStore.setBubbleEnabled(checked) }
            })
            Text(text = if (bubbleEnabled) "Bubble enabled" else "Bubble disabled")
        }
    }
}
