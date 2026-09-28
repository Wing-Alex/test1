package com.example

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class GlassesWebSocketClient(
    serverUri: URI,
    private val listener: Listener
) : WebSocketClient(serverUri) {

    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onMessageReceived(message: String)
        fun onErrorOccurred(error: String)
    }

    override fun onOpen(handshakedata: ServerHandshake?) {
        listener.onConnected()
    }

    override fun onMessage(message: String?) {
        if (message != null) {
            listener.onMessageReceived(message)
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        val details = if (!reason.isNullOrBlank()) reason else "Connection closed (code: $code)"
        listener.onDisconnected(details)
    }

    override fun onError(ex: Exception?) {
        val errorMsg = ex?.localizedMessage ?: "WebSocket error"
        listener.onErrorOccurred(errorMsg)
    }

    fun sendText(text: String): Boolean {
        return try {
            if (isOpen) {
                send(text)
                true
            } else {
                listener.onErrorOccurred("Cannot send: not connected")
                false
            }
        } catch (e: Exception) {
            listener.onErrorOccurred("Send error: ${e.message}")
            false
        }
    }
}
