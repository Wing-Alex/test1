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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val clientState by viewModel.clientState.collectAsStateWithLifecycle()

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

    BackHandler(enabled = currentMode != AppMode.PHONE_SERVER) {
        viewModel.setMode(AppMode.PHONE_SERVER)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // Clean Top Header with proper Safe Drawing WindowInsets
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "Wi-Fi",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = "INMO Link",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " • 雙向影音文字",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                NavigationBarItem(
                    selected = currentMode == AppMode.PHONE_SERVER,
                    onClick = { viewModel.setMode(AppMode.PHONE_SERVER) },
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("手機端 (Server)", fontSize = 10.sp) },
                    modifier = Modifier.testTag("tab_phone_server")
                )
                NavigationBarItem(
                    selected = currentMode == AppMode.GLASSES_CLIENT,
                    onClick = { viewModel.setMode(AppMode.GLASSES_CLIENT) },
                    icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text("眼鏡端 (Client)", fontSize = 10.sp) },
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
                        onToggleLiveFrame = { show -> viewModel.toggleServerRemoteVideo(show) },
                        onToggleConnectionCard = { viewModel.toggleServerConnectionCard() },
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
                        onToggleLiveFrame = { show -> viewModel.toggleClientRemoteVideo(show) },
                        onToggleConnectionCard = { viewModel.toggleClientConnectionCard() },
                        onInputChanged = { text -> viewModel.updateClientInput(text) },
                        onClearMessages = { viewModel.clearClientMessages() }
                    )
                }
            }
        }
    }
}
