package org.brightify.hyperdrive.krpc.ktor

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.protocol.ascension.ConnectionClosedException

class WebSocketSessionConnection(val webSocketSession: WebSocketSession): RPCConnection, CoroutineScope by webSocketSession {
    private companion object {
        val logger = Logger<WebSocketSessionConnection>()
    }
    override suspend fun receive(): SerializedFrame {
        try {
            while (isActive) {
                return when (val frame = webSocketSession.incoming.receive().also { logger.debug { "Received WebSocket frame: $it" } }) {
                    is Frame.Binary -> SerializedFrame.Binary(frame.readBytes())
                    is Frame.Text -> SerializedFrame.Text(frame.readText())
                    is Frame.Close -> {
                        throw ConnectionClosedException("Connection closed by the other party.")
                    }
                    is Frame.Ping -> {
                        webSocketSession.send(Frame.Pong(frame.readBytes()))
                        continue
                    }
                    is Frame.Pong -> continue
                    else -> continue
                }
            }
            throw ConnectionClosedException()
        } catch(e: ClosedReceiveChannelException) {
            throw ConnectionClosedException()
        }
    }

    override suspend fun send(frame: SerializedFrame) {
        if (webSocketSession.outgoing.isClosedForSend) {
            throw ConnectionClosedException()
        }
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