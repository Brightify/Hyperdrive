package org.brightify.hyperdrive.client.impl

import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.brightify.hyperdrive.krpc.api.IncomingRPCFrame
import org.brightify.hyperdrive.krpc.api.OutgoingRPCFrame
import org.brightify.hyperdrive.krpc.api.RPCEvent
import org.brightify.hyperdrive.krpc.api.RPCTransport
import org.brightify.hyperdrive.krpc.api.WebSocketFrameConverter
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class WebSocketClient(
    private val connectionScope: CoroutineScope,
    private val host: String = "localhost",
    private val port: Int = 8000,
    private val path: String = "/",
    private val frameConverter: WebSocketFrameConverter<Frame, RPCEvent.Upstream, RPCEvent.Downstream>
): RPCTransport<RPCEvent.Upstream, RPCEvent.Downstream> {
    private val httpClient = HttpClient {
        install(WebSockets)
    }

    private var ws: ClientWebSocketSession? = null
    private var connectingMutex = Mutex()

    override suspend fun send(frame: OutgoingRPCFrame<RPCEvent.Upstream>) {
        val session = connect()

        session.send(frameConverter.rpcFrameToWebSocketFrame(frame))
    }

    override suspend fun receive(): IncomingRPCFrame<RPCEvent.Downstream> {
        val session = connect()
        val frame = session.incoming.receive()

        return frameConverter.rpcFrameFromWebSocketFrame(frame)
    }

    private suspend fun connect(): ClientWebSocketSession {
        return connectingMutex.withLock {
            val oldSession = ws
            if (oldSession != null && oldSession.isActive) {
                return@withLock oldSession
            }
            val session = httpClient.webSocketSession(host = host, port = port, path = path)
            ws = session
            return@withLock session
        }
    }
}