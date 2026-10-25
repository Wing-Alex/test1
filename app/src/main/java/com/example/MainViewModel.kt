package com.example

import android.content.Context
import android.graphics.Bitmap
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
    val statusMessage: String = "Server stopped",
    val remoteLiveFrame: Bitmap? = null,
    val isLiveStreaming: Boolean = false,
    val isRecordingVoice: Boolean = false
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
    val errorMessage: String? = null,
    val remoteLiveFrame: Bitmap? = null,
    val isLiveStreaming: Boolean = false,
    val isRecordingVoice: Boolean = false
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
                        val parsed = ChatMessage.parseFromJson(message, "眼鏡 ($fromAddress)")
                        if (parsed != null) {
                            if (parsed.type == MessageType.VIDEO_FRAME) {
                                // Live video frame stream update
                                _serverState.update {
                                    it.copy(
                                        remoteLiveFrame = parsed.imageBitmap,
                                        isLiveStreaming = true
                                    )
                                }
                            } else {
                                _serverState.update {
                                    it.copy(messages = it.messages + parsed)
                                }
                            }
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
                    statusMessage = "Server 已停止",
                    remoteLiveFrame = null,
                    isLiveStreaming = false
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

        val jsonPayload = ChatMessage.createTextJson(trimmed, "手機 (Server)")
        viewModelScope.launch(Dispatchers.IO) {
            val sent = currentServer.broadcastText(jsonPayload)
            if (sent) {
                val outMsg = ChatMessage(
                    text = trimmed,
                    type = MessageType.TEXT,
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

    fun sendServerPhoto(bitmap: Bitmap) {
        val currentServer = serverInstance ?: return
        if (!_serverState.value.isRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            val json = ChatMessage.createPhotoJson(bitmap, "手機 (Server)")
            val sent = currentServer.broadcastText(json)
            if (sent) {
                val outMsg = ChatMessage(
                    text = "📷 [相片]",
                    type = MessageType.PHOTO,
                    isOutgoing = true,
                    senderLabel = "手機 (Server)",
                    imageBitmap = bitmap
                )
                _serverState.update {
                    it.copy(messages = it.messages + outMsg)
                }
            }
        }
    }

    fun sendServerVideoFrame(bitmap: Bitmap) {
        val currentServer = serverInstance ?: return
        if (!_serverState.value.isRunning) return

        viewModelScope.launch(Dispatchers.IO) {
            val json = ChatMessage.createVideoFrameJson(bitmap, "手機 (Server)")
            currentServer.broadcastText(json)
        }
    }

    fun startServerVoiceRecording() {
        _serverState.update { it.copy(isRecordingVoice = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val (audioData, duration) = AudioHelper.recordAudioClip(5)
            _serverState.update { it.copy(isRecordingVoice = false) }
            if (audioData.isNotEmpty() && duration > 0) {
                val currentServer = serverInstance
                if (currentServer != null && _serverState.value.isRunning) {
                    val json = ChatMessage.createAudioClipJson(audioData, duration, "手機 (Server)")
                    val sent = currentServer.broadcastText(json)
                    if (sent) {
                        val outMsg = ChatMessage(
                            text = "🎤 [語音訊息 ${duration}s]",
                            type = MessageType.AUDIO_CLIP,
                            isOutgoing = true,
                            senderLabel = "手機 (Server)",
                            audioDurationSec = duration,
                            audioData = audioData
                        )
                        _serverState.update { it.copy(messages = it.messages + outMsg) }
                    }
                }
            }
        }
    }

    fun stopServerVoiceRecording() {
        AudioHelper.stopRecording()
        _serverState.update { it.copy(isRecordingVoice = false) }
    }

    fun clearServerMessages() {
        _serverState.update { it.copy(messages = emptyList(), remoteLiveFrame = null) }
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

    fun updateTargetPort(port: String) {
        _clientState.update { it.copy(targetPort = port.trim()) }
    }

    fun updateClientInput(text: String) {
        _clientState.update { it.copy(inputText = text) }
    }

    /**
     * Connect client. If context is provided, auto-detects the Wi-Fi gateway IP first!
     */
    fun connectClient(context: Context? = null) {
        var ip = _clientState.value.targetIp
        if (context != null) {
            val autoGateway = NetworkUtils.getWifiGatewayIp(context)
            if (!autoGateway.isNullOrBlank()) {
                ip = autoGateway
            }
        }

        val (cleanIp, portStr) = NetworkUtils.sanitizeHostAndPort(ip, _clientState.value.targetPort)
        val port = portStr.toIntOrNull() ?: 8080

        if (cleanIp.isEmpty()) {
            _clientState.update { it.copy(errorMessage = "請輸入手機 Server IP") }
            return
        }

        _clientState.update {
            it.copy(
                targetIp = cleanIp,
                targetPort = port.toString(),
                isConnecting = true,
                errorMessage = null,
                statusMessage = "連線中至 ws://$cleanIp:$port..."
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                clientInstance?.close()
                val uri = URI("ws://$cleanIp:$port")
                val client = GlassesWebSocketClient(uri, object : GlassesWebSocketClient.Listener {
                    override fun onConnected() {
                        _clientState.update {
                            it.copy(
                                isConnected = true,
                                isConnecting = false,
                                statusMessage = "已連線至 ws://$cleanIp:$port",
                                errorMessage = null
                            )
                        }
                    }

                    override fun onDisconnected(reason: String) {
                        _clientState.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusMessage = "已中斷連線: $reason",
                                remoteLiveFrame = null,
                                isLiveStreaming = false
                            )
                        }
                    }

                    override fun onMessageReceived(message: String) {
                        val parsed = ChatMessage.parseFromJson(message, "手機")
                        if (parsed != null) {
                            if (parsed.type == MessageType.VIDEO_FRAME) {
                                _clientState.update {
                                    it.copy(
                                        remoteLiveFrame = parsed.imageBitmap,
                                        isLiveStreaming = true
                                    )
                                }
                            } else {
                                _clientState.update {
                                    it.copy(messages = it.messages + parsed)
                                }
                            }
                        }
                    }

                    override fun onErrorOccurred(error: String) {
                        val friendlyError = "連線失敗 ($error)\n\n" +
                                "🔍 排查建議：\n" +
                                "1. 請確認手機端已開啟 App 並點擊「啟動 (8080)」\n" +
                                "2. 請確認眼鏡已連上手機熱點 Wi-Fi\n" +
                                "3. 手機熱點若有「AP 隔離 / 允許設備互訪」，請開啟允許互訪\n" +
                                "4. 請檢查手機 Server 上顯示的 IP 與眼鏡端填入的是否完全一致"
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
                    statusMessage = "已中斷連線",
                    remoteLiveFrame = null,
                    isLiveStreaming = false
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

        val jsonPayload = ChatMessage.createTextJson(trimmed, "眼鏡")
        viewModelScope.launch(Dispatchers.IO) {
            val sent = client.sendText(jsonPayload)
            if (sent) {
                val outMsg = ChatMessage(
                    text = trimmed,
                    type = MessageType.TEXT,
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

    fun sendClientPhoto(bitmap: Bitmap) {
        val client = clientInstance ?: return
        if (!client.isOpen) return

        viewModelScope.launch(Dispatchers.IO) {
            val json = ChatMessage.createPhotoJson(bitmap, "眼鏡")
            val sent = client.sendText(json)
            if (sent) {
                val outMsg = ChatMessage(
                    text = "📷 [相片]",
                    type = MessageType.PHOTO,
                    isOutgoing = true,
                    senderLabel = "眼鏡",
                    imageBitmap = bitmap
                )
                _clientState.update {
                    it.copy(messages = it.messages + outMsg)
                }
            }
        }
    }

    fun sendClientVideoFrame(bitmap: Bitmap) {
        val client = clientInstance ?: return
        if (!client.isOpen) return

        viewModelScope.launch(Dispatchers.IO) {
            val json = ChatMessage.createVideoFrameJson(bitmap, "眼鏡")
            client.sendText(json)
        }
    }

    fun startClientVoiceRecording() {
        _clientState.update { it.copy(isRecordingVoice = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val (audioData, duration) = AudioHelper.recordAudioClip(5)
            _clientState.update { it.copy(isRecordingVoice = false) }
            if (audioData.isNotEmpty() && duration > 0) {
                val client = clientInstance
                if (client != null && client.isOpen) {
                    val json = ChatMessage.createAudioClipJson(audioData, duration, "眼鏡")
                    val sent = client.sendText(json)
                    if (sent) {
                        val outMsg = ChatMessage(
                            text = "🎤 [語音訊息 ${duration}s]",
                            type = MessageType.AUDIO_CLIP,
                            isOutgoing = true,
                            senderLabel = "眼鏡",
                            audioDurationSec = duration,
                            audioData = audioData
                        )
                        _clientState.update { it.copy(messages = it.messages + outMsg) }
                    }
                }
            }
        }
    }

    fun stopClientVoiceRecording() {
        AudioHelper.stopRecording()
        _clientState.update { it.copy(isRecordingVoice = false) }
    }

    fun clearClientMessages() {
        _clientState.update { it.copy(messages = emptyList(), remoteLiveFrame = null) }
    }

    fun playVoiceMessage(audioData: ByteArray) {
        viewModelScope.launch {
            AudioHelper.playAudioClip(audioData)
        }
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
