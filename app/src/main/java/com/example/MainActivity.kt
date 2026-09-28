package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val clientState by viewModel.clientState.collectAsStateWithLifecycle()

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
                                text = "INMO Air 3 & Android 雙向文字測試",
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
                NavigationBarItem(
                    selected = currentMode == AppMode.GUIDE,
                    onClick = { viewModel.setMode(AppMode.GUIDE) },
                    icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                    label = { Text("測試教學") },
                    modifier = Modifier.testTag("tab_guide")
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
                        onInputChanged = { text -> viewModel.updateServerInput(text) },
                        onClearMessages = { viewModel.clearServerMessages() }
                    )
                }
                AppMode.GLASSES_CLIENT -> {
                    GlassesClientScreen(
                        state = clientState,
                        onIpChanged = { ip -> viewModel.updateTargetIp(ip) },
                        onPortChanged = { port -> viewModel.updateTargetPort(port) },
                        onConnect = { viewModel.connectClient() },
                        onDisconnect = { viewModel.disconnectClient() },
                        onSendMessage = { text -> viewModel.sendClientMessage(text) },
                        onInputChanged = { text -> viewModel.updateClientInput(text) },
                        onClearMessages = { viewModel.clearClientMessages() }
                    )
                }
                AppMode.GUIDE -> {
                    IntegrationGuideScreen()
                }
            }
        }
    }
}
