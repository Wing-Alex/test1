package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntegrationGuideScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val clientCodeSnippet = """
// 複製此檔案至現有眼鏡專案 (SpeakerDiarazation-Gesture)
// 檔案名稱：InmoGlassesWebSocketClient.kt
package com.example // 修改為您的 package

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class InmoGlassesWebSocketClient(
    phoneIp: String,
    port: Int = 8080,
    private val onMessage: (String) -> Unit,
    private val onStatus: (Boolean, String) -> Unit
) : WebSocketClient(URI("ws://${'$'}phoneIp:${'$'}port")) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        onStatus(true, "Connected to Phone")
    }

    override fun onMessage(message: String?) {
        message?.let { onMessage(it) }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        onStatus(false, "Disconnected: ${'$'}reason")
    }

    override fun onError(ex: Exception?) {
        onStatus(false, "Error: ${'$'}{ex?.message}")
    }

    fun sendText(text: String): Boolean {
        return if (isOpen) {
            send(text)
            true
        } else false
    }
}

/* 使用範例：
val client = InmoGlassesWebSocketClient(
    phoneIp = "192.168.43.1",
    port = 8080,
    onMessage = { text ->
        // 在眼鏡畫面上顯示收到的文字 (例如 "Hello Glasses")
        println("收到手機文字: ${'$'}text")
    },
    onStatus = { connected, msg ->
        println("連線狀態: ${'$'}connected (${'$'}msg)")
    }
)
client.connect()

// 傳送給手機：
client.sendText("Hello Phone")
*/
""".trimIndent()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Architecture Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "架構與測試流程",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "• 手機端 = WebSocket Server (監聽 Port 8080)\n• INMO Air 3 = WebSocket Client (連線至手機 IP:8080)\n• 無需外網、無需藍牙配對，完全透過本地 Wi-Fi 熱點雙向即時通訊。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // 4 Steps Guide
        Text(
            text = "4 步完成雙向通訊測試",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        StepItem(
            stepNumber = "1",
            icon = Icons.Default.Wifi,
            title = "手機手動開啟 Wi-Fi 熱點 (Hotspot)",
            description = "進入手機「設定」>「個人熱點」開啟 Wi-Fi 熱點分享。"
        )

        StepItem(
            stepNumber = "2",
            icon = Icons.Default.Check,
            title = "INMO Air 3 眼鏡連接手機熱點",
            description = "在眼鏡的 Wi-Fi 設定中，找到並連接到手機發出的熱點名稱。"
        )

        StepItem(
            stepNumber = "3",
            icon = Icons.Default.DirectionsRun,
            title = "手機 App 啟動 Server",
            description = "切換至上方「手機端 (Server)」頁面，點擊「啟動 (Port 8080)」。App 會顯示手機的 Hotspot IP (通常為 192.168.43.1)。"
        )

        StepItem(
            stepNumber = "4",
            icon = Icons.Default.Check,
            title = "眼鏡連線並互傳測試",
            description = "切換至「眼鏡端 (Client)」(或在眼鏡上開啟)，輸入手機 IP，按「連線」。\n• 眼鏡點擊「快速傳送：Hello Phone」-> 手機立即收到\n• 手機點擊「快速傳送：Hello Glasses」-> 眼鏡立即收到"
        )

        Spacer(Modifier.height(8.dp))

        // Integration for existing GitHub Project Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "現有專案 (SpeakerDiarazation-Gesture) 整合",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "若要將 Client 整合進現有眼鏡專案，只需 2 步：\n1. app/build.gradle 加入：implementation(\"org.java-websocket:Java-WebSocket:1.5.6\")\n2. AndroidManifest.xml 加入 INTERNET 權限及 android:usesCleartextTraffic=\"true\"\n3. 複製下方極簡輔助類即可直接使用！",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Code snippet container
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "InmoGlassesWebSocketClient.kt",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace
                            )
                            Button(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("InmoClientCode", clientCodeSnippet))
                                    Toast.makeText(context, "已複製程式碼至剪貼簿！", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("複製程式碼")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = clientCodeSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFFE2E8F0),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stepNumber,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
