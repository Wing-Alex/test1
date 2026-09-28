package com.example

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class PhoneWebSocketServer(
    port: Int = 8080,
    private val listener: Listener
) : WebSocketServer(InetSocketAddress(port)) {

    interface Listener {
        fun onServerStarted(port: Int)
        fun onClientConnected(clientAddress: String, totalClients: Int)
        fun onClientDisconnected(clientAddress: String, totalClients: Int)
        fun onMessageReceived(message: String, fromAddress: String)
        fun onErrorOccurred(error: String)
        fun onServerStopped()
    }

    init {
        isReuseAddr = true
    }

    override fun onStart() {
        listener.onServerStarted(port)
    }

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        val clientAddress = conn?.remoteSocketAddress?.toString() ?: "Unknown"
        val total = connections?.size ?: 0
        listener.onClientConnected(clientAddress, total)
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        val clientAddress = conn?.remoteSocketAddress?.toString() ?: "Unknown"
        val total = connections?.size ?: 0
        listener.onClientDisconnected(clientAddress, total)
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message != null) {
            val from = conn?.remoteSocketAddress?.toString() ?: "Client"
            listener.onMessageReceived(message, from)
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        val errorMsg = ex?.localizedMessage ?: "Unknown WebSocket Server error"
        listener.onErrorOccurred(errorMsg)
    }

    fun broadcastText(message: String): Boolean {
        return try {
            broadcast(message)
            true
        } catch (e: Exception) {
            listener.onErrorOccurred("Failed to send: ${e.message}")
            false
        }
    }

    fun stopGracefully() {
        try {
            stop(1000)
            listener.onServerStopped()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
