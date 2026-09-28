package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI

enum class AppMode {
    PHONE_SERVER,
    GLASSES_CLIENT
}

data class ServerUiState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val primaryIp: String = "Detecting...",
    val allIps: List<NetworkUtils.NetworkIpInfo> = emptyList(),
    val isEmulator: Boolean = false,
    val connectedClientsCount: Int = 0,
    val lastClientAddress: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val statusMessage: String = "Server stopped"
)

data class ClientUiState(
    val targetIp: String = "192.168.43.1",
    val targetPort: String = "8080",
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isEmulator: Boolean = false,
    val statusMessage: String = "Disconnected",
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val errorMessage: String? = null
)

class MainViewModel : ViewModel() {

    private val _currentMode = MutableStateFlow(AppMode.PHONE_SERVER)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _serverState = MutableStateFlow(ServerUiState())
    val serverState: StateFlow<ServerUiState> = _serverState.asStateFlow()

    private val _clientState = MutableStateFlow(ClientUiState())
    val clientState: StateFlow<ClientUiState> = _clientState.asStateFlow()

    private var serverInstance: PhoneWebSocketServer? = null
    private var clientInstance: GlassesWebSocketClient? = null

    init {
        refreshServerNetwork()
    }

    fun setMode(mode: AppMode) {
        _currentMode.value = mode
        if (mode == AppMode.PHONE_SERVER) {
            refreshServerNetwork()
        }
    }

    fun refreshServerNetwork() {
        viewModelScope.launch(Dispatchers.IO) {
            val ips = NetworkUtils.getLocalIPv4Addresses()
            val primary = NetworkUtils.getPrimaryIp()
            val isEmu = NetworkUtils.isEmulator()
            _serverState.update {
                it.copy(
                    allIps = ips,
                    primaryIp = primary,
                    isEmulator = isEmu
                )
            }
            _clientState.update {
                it.copy(isEmulator = isEmu)
            }
        }
    }

    // --- Phone Server Operations ---

    fun startServer(port: Int = 8080) {
        if (_serverState.value.isRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                serverInstance?.stopGracefully()
                val server = PhoneWebSocketServer(port, object : PhoneWebSocketServer.Listener {
                    override fun onServerStarted(port: Int) {
                        _serverState.update {
                            it.copy(
                                isRunning = true,
                                port = port,
                                statusMessage = "Server 運行中 (Port $port)"
                            )
                        }
                    }

                    override fun onClientConnected(clientAddress: String, totalClients: Int) {
                        _serverState.update {
                            it.copy(
                                connectedClientsCount = totalClients,
                                lastClientAddress = clientAddress,
                                statusMessage = "已連線裝置: $clientAddress"
                            )
                        }
                    }

                    override fun onClientDisconnected(clientAddress: String, totalClients: Int) {
                        _serverState.update {
                            it.copy(
                                connectedClientsCount = totalClients,
                                statusMessage = if (totalClients == 0) "客戶端已中斷連線" else "$totalClients 裝置連線中"
                            )
                        }
                    }

                    override fun onMessageReceived(message: String, fromAddress: String) {
                        val chatMsg = ChatMessage(
                            text = message,
                            isOutgoing = false,
                            senderLabel = "眼鏡 ($fromAddress)"
                        )
                        _serverState.update {
                            it.copy(messages = it.messages + chatMsg)
                        }
                    }

                    override fun onErrorOccurred(error: String) {
                        _serverState.update {
                            it.copy(statusMessage = "Server 錯誤: $error")
                        }
                    }

                    override fun onServerStopped() {
                        _serverState.update {
                            it.copy(
                                isRunning = false,
                                connectedClientsCount = 0,
                                statusMessage = "Server 已停止"
                            )
                        }
                    }
                })
                serverInstance = server
                server.start()
                refreshServerNetwork()
            } catch (e: Exception) {
                _serverState.update {
                    it.copy(
                        isRunning = false,
                        statusMessage = "啟動失敗: ${e.message}"
                    )
                }
            }
        }
    }

    fun stopServer() {
        viewModelScope.launch(Dispatchers.IO) {
            serverInstance?.stopGracefully()
            serverInstance = null
            _serverState.update {
                it.copy(
                    isRunning = false,
                    connectedClientsCount = 0,
                    statusMessage = "Server 已停止"
                )
            }
        }
    }

    fun updateServerInput(text: String) {
        _serverState.update { it.copy(inputText = text) }
    }

    fun sendServerMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val currentServer = serverInstance
        if (currentServer == null || !_serverState.value.isRunning) {
            _serverState.update { it.copy(statusMessage = "無法發送：Server 尚未啟動") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sent = currentServer.broadcastText(trimmed)
            if (sent) {
                val outMsg = ChatMessage(
                    text = trimmed,
                    isOutgoing = true,
                    senderLabel = "手機 (Server)"
                )
                _serverState.update {
                    it.copy(
                        messages = it.messages + outMsg,
                        inputText = ""
                    )
                }
            }
        }
    }

    fun clearServerMessages() {
        _serverState.update { it.copy(messages = emptyList()) }
    }

    // --- INMO Air 3 Glasses Client Operations ---

    fun updateTargetIp(rawInput: String) {
        val (cleanIp, cleanPort) = NetworkUtils.sanitizeHostAndPort(rawInput, _clientState.value.targetPort)
        _clientState.update {
            it.copy(
                targetIp = cleanIp,
                targetPort = cleanPort,
                errorMessage = null
            )
        }
    }

    fun setTargetIpPreset(ip: String) {
        _clientState.update {
            it.copy(
                targetIp = ip,
                errorMessage = null
            )
        }
    }

    fun updateTargetPort(port: String) {
        _clientState.update { it.copy(targetPort = port.trim()) }
    }

    fun updateClientInput(text: String) {
        _clientState.update { it.copy(inputText = text) }
    }

    fun connectClient() {
        val (ip, portStr) = NetworkUtils.sanitizeHostAndPort(
            _clientState.value.targetIp,
            _clientState.value.targetPort
        )
        val port = portStr.toIntOrNull() ?: 8080

        if (ip.isEmpty()) {
            _clientState.update { it.copy(errorMessage = "請輸入手機 Server IP") }
            return
        }

        _clientState.update {
            it.copy(
                targetIp = ip,
                targetPort = port.toString(),
                isConnecting = true,
                errorMessage = null,
                statusMessage = "連線中至 ws://$ip:$port..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                clientInstance?.close()
                val uri = URI("ws://$ip:$port")
                val client = GlassesWebSocketClient(uri, object : GlassesWebSocketClient.Listener {
                    override fun onConnected() {
                        _clientState.update {
                            it.copy(
                                isConnected = true,
                                isConnecting = false,
                                statusMessage = "已連線至 ws://$ip:$port",
                                errorMessage = null
                            )
                        }
                    }

                    override fun onDisconnected(reason: String) {
                        _clientState.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusMessage = "已中斷連線: $reason"
                            )
                        }
                    }

                    override fun onMessageReceived(message: String) {
                        val chatMsg = ChatMessage(
                            text = message,
                            isOutgoing = false,
                            senderLabel = "手機"
                        )
                        _clientState.update {
                            it.copy(messages = it.messages + chatMsg)
                        }
                    }

                    override fun onErrorOccurred(error: String) {
                        val friendlyError = if (error.contains("ETIMEDOUT") || error.contains("failed to connect") || error.contains("ECONNREFUSED")) {
                            if (ip.startsWith("10.0.2.") && ip != "10.0.2.2") {
                                "連線失敗。\n⚠️ 兩台模擬器互連時，請勿輸入「10.0.2.16」，因為每個模擬器的 10.0.2.x 都是獨立虛擬網段。\n✅ 正確做法：\n1. 請將 IP 改填「10.0.2.2」\n2. 在電腦終端機 (Terminal) 執行：\n   adb forward tcp:8080 tcp:8080\n若是實體手機熱點，請輸入「192.168.43.1」。"
                            } else if (ip == "10.0.2.2") {
                                "連線失敗。\n若使用兩台電腦模擬器測試，請在電腦 Terminal 執行：\nadb forward tcp:8080 tcp:8080\n然後確認 Server 端已點擊「啟動」再連線！"
                            } else {
                                "連線失敗 ($error)。\n請確認：\n1. Server 端已點擊「啟動 (Port 8080)」\n2. 兩台裝置在同一個 Wi-Fi 或手機熱點下\n3. 若使用實體手機熱點，IP 通常為 192.168.43.1"
                            }
                        } else {
                            error
                        }
                        _clientState.update {
                            it.copy(
                                isConnecting = false,
                                errorMessage = friendlyError,
                                statusMessage = "連線失敗"
                            )
                        }
                    }
                })
                clientInstance = client
                client.connect()
            } catch (e: Exception) {
                _clientState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        errorMessage = e.message,
                        statusMessage = "連線異常: ${e.message}"
                    )
                }
            }
        }
    }

    fun disconnectClient() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                clientInstance?.close()
                clientInstance = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _clientState.update {
                it.copy(
                    isConnected = false,
                    isConnecting = false,
                    statusMessage = "已中斷連線"
                )
            }
        }
    }

    fun sendClientMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val client = clientInstance

        if (client == null || !client.isOpen) {
            _clientState.update { it.copy(errorMessage = "無法傳送：尚未連線至手機") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sent = client.sendText(trimmed)
            if (sent) {
                val outMsg = ChatMessage(
                    text = trimmed,
                    isOutgoing = true,
                    senderLabel = "眼鏡"
                )
                _clientState.update {
                    it.copy(
                        messages = it.messages + outMsg,
                        inputText = ""
                    )
                }
            }
        }
    }

    fun clearClientMessages() {
        _clientState.update { it.copy(messages = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            serverInstance?.stopGracefully()
            clientInstance?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
