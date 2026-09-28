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
    GLASSES_CLIENT,
    GUIDE
}

data class ServerUiState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val primaryIp: String = "Detecting...",
    val allIps: List<NetworkUtils.NetworkIpInfo> = emptyList(),
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
            _serverState.update {
                it.copy(
                    allIps = ips,
                    primaryIp = primary
                )
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
                                statusMessage = "Server running on port $port"
                            )
                        }
                    }

                    override fun onClientConnected(clientAddress: String, totalClients: Int) {
                        _serverState.update {
                            it.copy(
                                connectedClientsCount = totalClients,
                                lastClientAddress = clientAddress,
                                statusMessage = "Client connected: $clientAddress"
                            )
                        }
                    }

                    override fun onClientDisconnected(clientAddress: String, totalClients: Int) {
                        _serverState.update {
                            it.copy(
                                connectedClientsCount = totalClients,
                                statusMessage = if (totalClients == 0) "Client disconnected" else "$totalClients client(s) connected"
                            )
                        }
                    }

                    override fun onMessageReceived(message: String, fromAddress: String) {
                        val chatMsg = ChatMessage(
                            text = message,
                            isOutgoing = false,
                            senderLabel = "Glasses ($fromAddress)"
                        )
                        _serverState.update {
                            it.copy(messages = it.messages + chatMsg)
                        }
                    }

                    override fun onErrorOccurred(error: String) {
                        _serverState.update {
                            it.copy(statusMessage = "Server error: $error")
                        }
                    }

                    override fun onServerStopped() {
                        _serverState.update {
                            it.copy(
                                isRunning = false,
                                connectedClientsCount = 0,
                                statusMessage = "Server stopped"
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
                        statusMessage = "Start failed: ${e.message}"
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
                    statusMessage = "Server stopped"
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
            _serverState.update { it.copy(statusMessage = "Cannot send: Server is not running") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sent = currentServer.broadcastText(trimmed)
            if (sent) {
                val outMsg = ChatMessage(
                    text = trimmed,
                    isOutgoing = true,
                    senderLabel = "Phone (Server)"
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

    fun updateTargetIp(ip: String) {
        _clientState.update { it.copy(targetIp = ip.trim(), errorMessage = null) }
    }

    fun updateTargetPort(port: String) {
        _clientState.update { it.copy(targetPort = port.trim()) }
    }

    fun updateClientInput(text: String) {
        _clientState.update { it.copy(inputText = text) }
    }

    fun connectClient() {
        val ip = _clientState.value.targetIp.trim()
        val portStr = _clientState.value.targetPort.trim()
        val port = portStr.toIntOrNull() ?: 8080

        if (ip.isEmpty()) {
            _clientState.update { it.copy(errorMessage = "Please enter Phone Server IP") }
            return
        }

        _clientState.update {
            it.copy(
                isConnecting = true,
                errorMessage = null,
                statusMessage = "Connecting to ws://$ip:$port..."
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
                                statusMessage = "Connected to ws://$ip:$port",
                                errorMessage = null
                            )
                        }
                    }

                    override fun onDisconnected(reason: String) {
                        _clientState.update {
                            it.copy(
                                isConnected = false,
                                isConnecting = false,
                                statusMessage = "Disconnected: $reason"
                            )
                        }
                    }

                    override fun onMessageReceived(message: String) {
                        val chatMsg = ChatMessage(
                            text = message,
                            isOutgoing = false,
                            senderLabel = "Phone"
                        )
                        _clientState.update {
                            it.copy(messages = it.messages + chatMsg)
                        }
                    }

                    override fun onErrorOccurred(error: String) {
                        _clientState.update {
                            it.copy(
                                isConnecting = false,
                                errorMessage = error,
                                statusMessage = "Error: $error"
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
                        statusMessage = "Connection failed: ${e.message}"
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
                    statusMessage = "Disconnected"
                )
            }
        }
    }

    fun sendClientMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val client = clientInstance

        if (client == null || !client.isOpen) {
            _clientState.update { it.copy(errorMessage = "Cannot send: Not connected to phone") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sent = client.sendText(trimmed)
            if (sent) {
                val outMsg = ChatMessage(
                    text = trimmed,
                    isOutgoing = true,
                    senderLabel = "Glasses"
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
