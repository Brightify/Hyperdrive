package org.brightify.hyperdrive.krpc.ktor

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedFrame

class WebSocketSessionConnection(val webSocketSession: WebSocketSession): RPCConnection, CoroutineScope by webSocketSession {
    private companion object {
        val logger = Logger<WebSocketSessionConnection>()
    }
    override suspend fun receive(): SerializedFrame {
        while (true) {
            return when (val frame = webSocketSession.incoming.receive().also { logger.debug { "Received WebSocket frame: $it" } }) {
                is Frame.Binary -> SerializedFrame.Binary(frame.readBytes())
                is Frame.Text -> SerializedFrame.Text(frame.readText())
                is Frame.Close -> {
                    throw CancellationException("Connection closed by the other party.")
                }
                is Frame.Ping -> {
                    webSocketSession.send(Frame.Pong(frame.readBytes()))
                    continue
                }
                is Frame.Pong -> continue
            }
        }
    }

    override suspend fun send(frame: SerializedFrame) {
        try {
            logger.debug { "Sending WebSocket frame: $frame" }
            /* TODO: Do exhaustive */ when (frame) {
                is SerializedFrame.Binary -> webSocketSession.send(Frame.Binary(true, frame.binary))
                is SerializedFrame.Text -> webSocketSession.send(Frame.Text(frame.text))
            }
        } catch (t: Throwable) {
            throw t
        }
    }

    override suspend fun close() {
        logger.trace { "Closing connection $this" }
        webSocketSession.close()
    }
}