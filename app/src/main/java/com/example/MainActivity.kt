package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val clientState by viewModel.clientState.collectAsStateWithLifecycle()

    // Request Camera and Audio permissions at runtime for media features
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    // Handle back button: if in submode, return to default phone server mode
    BackHandler(enabled = currentMode != AppMode.PHONE_SERVER) {
        viewModel.setMode(AppMode.PHONE_SERVER)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Wi-Fi",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "INMO Link",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "INMO Air 3 & Android 雙向視訊・文字・語音",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBarItem(
                    selected = currentMode == AppMode.PHONE_SERVER,
                    onClick = { viewModel.setMode(AppMode.PHONE_SERVER) },
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
                    label = { Text("手機端 (Server)") },
                    modifier = Modifier.testTag("tab_phone_server")
                )
                NavigationBarItem(
                    selected = currentMode == AppMode.GLASSES_CLIENT,
                    onClick = { viewModel.setMode(AppMode.GLASSES_CLIENT) },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    label = { Text("眼鏡端 (Client)") },
                    modifier = Modifier.testTag("tab_glasses_client")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentMode) {
                AppMode.PHONE_SERVER -> {
                    PhoneServerScreen(
                        state = serverState,
                        onStartServer = { port -> viewModel.startServer(port) },
                        onStopServer = { viewModel.stopServer() },
                        onRefreshNetwork = { viewModel.refreshServerNetwork() },
                        onSendMessage = { text -> viewModel.sendServerMessage(text) },
                        onSendPhoto = { bitmap -> viewModel.sendServerPhoto(bitmap) },
                        onSendVideoFrame = { frame -> viewModel.sendServerVideoFrame(frame) },
                        onStartVoiceRecord = { viewModel.startServerVoiceRecording() },
                        onStopVoiceRecord = { viewModel.stopServerVoiceRecording() },
                        onPlayAudio = { data -> viewModel.playVoiceMessage(data) },
                        onInputChanged = { text -> viewModel.updateServerInput(text) },
                        onClearMessages = { viewModel.clearServerMessages() }
                    )
                }
                AppMode.GLASSES_CLIENT -> {
                    GlassesClientScreen(
                        state = clientState,
                        onIpChanged = { ip -> viewModel.updateTargetIp(ip) },
                        onPortChanged = { port -> viewModel.updateTargetPort(port) },
                        onConnect = { ctx -> viewModel.connectClient(ctx) },
                        onDisconnect = { viewModel.disconnectClient() },
                        onSendMessage = { text -> viewModel.sendClientMessage(text) },
                        onSendPhoto = { bitmap -> viewModel.sendClientPhoto(bitmap) },
                        onSendVideoFrame = { frame -> viewModel.sendClientVideoFrame(frame) },
                        onStartVoiceRecord = { viewModel.startClientVoiceRecording() },
                        onStopVoiceRecord = { viewModel.stopClientVoiceRecording() },
                        onPlayAudio = { data -> viewModel.playVoiceMessage(data) },
                        onInputChanged = { text -> viewModel.updateClientInput(text) },
                        onClearMessages = { viewModel.clearClientMessages() }
                    )
                }
            }
        }
    }
}
