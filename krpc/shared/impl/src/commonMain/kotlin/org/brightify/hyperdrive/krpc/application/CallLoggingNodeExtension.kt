package org.brightify.hyperdrive.krpc.application

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.krpc.RPCTransport
import org.brightify.hyperdrive.krpc.description.ColdBistreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdDownstreamCallDescription
import org.brightify.hyperdrive.krpc.description.ColdUpstreamCallDescription
import org.brightify.hyperdrive.krpc.description.RunnableCallDescription
import org.brightify.hyperdrive.krpc.description.SingleCallDescription

class CallLoggingNodeExtension(
    private val logger: Logger,
    private val levels: LoggingLevels,
): RPCNodeExtension {

    data class LoggingLevels(
        val bind: LoggingLevel = LoggingLevel.Info,
        val callOpen: LoggingLevel = LoggingLevel.Info,
        val callResponse: LoggingLevel = LoggingLevel.Info,
        val callError: LoggingLevel = LoggingLevel.Warn,
        val streamStart: LoggingLevel = LoggingLevel.Debug,
        val streamItem: LoggingLevel = LoggingLevel.Trace,
        val streamEnd: LoggingLevel = LoggingLevel.Debug,
        val streamError: LoggingLevel = LoggingLevel.Warn,
    )

    enum class Direction(val icon: String) {
        Upstream("↑"),
        Downstream("↓");
    }

    override suspend fun bind(transport: RPCTransport, contract: RPCNode.Contract) {
        logger.logIfEnabled(levels.bind) { "Logging enabled for $transport" }
    }

    private suspend inline fun <T> logRequest(call: String, crossinline request: suspend () -> T): T {
        logger.logIfEnabled(levels.callOpen) { "${Direction.Upstream.icon} REQUEST: $call" }
        return try {
            val response = request()
            logger.logIfEnabled(levels.callResponse) { "${Direction.Downstream.icon} SUCCESS: $call = $response" }
            response
        } catch (t: Throwable) {
            logger.logIfEnabled(levels.callError, t) { "${Direction.Downstream.icon} ERROR: $call thrown ${t.message}" }
            throw t
        }
    }

    private suspend inline fun <T> logStream(call: String, direction: Direction, stream: Flow<T>): Flow<T> {
        return flow {
            var itemIndex = 0L
            emitAll(
                stream
                    .onStart {
                        logger.logIfEnabled(levels.streamStart) { "${direction.icon} STREAM START: $call" }
                    }
                    .onEach {
                        logger.logIfEnabled(levels.streamItem) { "${direction.icon} STREAM ITEM(${itemIndex++}): $call = $it" }
                    }
                    .onCompletion {
                        if (it != null && it !is CancellationException) {
                            logger.logIfEnabled(levels.streamError, it) { "${direction.icon} STREAM ERROR: $call" }
                        } else {
                            logger.logIfEnabled(levels.streamEnd) { "${direction.icon} STREAM END: $call" }
                        }
                    }
            )
        }
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptIncomingSingleCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.Single<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE,
    ): RESPONSE {
        return logRequest("${call.identifier}($payload)") {
            super.interceptIncomingSingleCall(payload, call, next)
        }
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptIncomingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdUpstream<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE {
        val callDescription = "${call.identifier}($payload)"
        return logRequest(callDescription) {
            super.interceptIncomingUpstreamCall(payload, logStream(callDescription, Direction.Upstream, stream), call, next)
        }
    }

    override suspend fun <PAYLOAD, SERVER_STREAM> interceptIncomingDownstreamCall(
        payload: PAYLOAD,
        call: RunnableCallDescription.ColdDownstream<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        val callDescription = "${call.identifier}($payload)"
        return logRequest(callDescription) {
            logStream(
                callDescription,
                Direction.Downstream,
                super.interceptIncomingDownstreamCall(payload, call, next)
            )
        }
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptIncomingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: RunnableCallDescription.ColdBistream<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        val callDescription = "${call.identifier}($payload)"
        return logRequest(callDescription) {
            logStream(
                callDescription,
                Direction.Downstream,
                super.interceptIncomingBistreamCall(payload, logStream(callDescription, Direction.Upstream, stream), call, next)
            )
        }
    }

    override suspend fun <PAYLOAD, RESPONSE> interceptOutgoingSingleCall(
        payload: PAYLOAD,
        call: SingleCallDescription<PAYLOAD, RESPONSE>,
        next: suspend (PAYLOAD) -> RESPONSE
    ): RESPONSE {
        return logRequest("${call.identifier}($payload)") {
            super.interceptOutgoingSingleCall(payload, call, next)
        }
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, RESPONSE> interceptOutgoingUpstreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdUpstreamCallDescription<PAYLOAD, CLIENT_STREAM, RESPONSE>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> RESPONSE,
    ): RESPONSE {
        val callDescription = "${call.identifier}($payload)"
        return logRequest(callDescription) {
            super.interceptOutgoingUpstreamCall(payload, logStream(callDescription, Direction.Upstream, stream), call, next)
        }
    }

    override suspend fun <PAYLOAD, SERVER_STREAM> interceptOutgoingDownstreamCall(
        payload: PAYLOAD,
        call: ColdDownstreamCallDescription<PAYLOAD, SERVER_STREAM>,
        next: suspend (PAYLOAD) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        val callDescription = "${call.identifier}($payload)"
        return logRequest(callDescription) {
            logStream(
                callDescription,
                Direction.Downstream,
                super.interceptOutgoingDownstreamCall(payload, call, next)
            )
        }
    }

    override suspend fun <PAYLOAD, CLIENT_STREAM, SERVER_STREAM> interceptOutgoingBistreamCall(
        payload: PAYLOAD,
        stream: Flow<CLIENT_STREAM>,
        call: ColdBistreamCallDescription<PAYLOAD, CLIENT_STREAM, SERVER_STREAM>,
        next: suspend (PAYLOAD, Flow<CLIENT_STREAM>) -> Flow<SERVER_STREAM>,
    ): Flow<SERVER_STREAM> {
        val callDescription = "${call.identifier}($payload)"
        return logRequest(callDescription) {
            logStream(
                callDescription,
                Direction.Downstream,
                super.interceptOutgoingBistreamCall(payload, logStream(callDescription, Direction.Upstream, stream), call, next)
            )
        }
    }

    object Identifier: RPCNodeExtension.Identifier<CallLoggingNodeExtension> {
        override val uniqueIdentifier = "builtin:CallLogging"
        override val extensionClass = CallLoggingNodeExtension::class
    }

    class Factory(
        private val logger: Logger = Logger<CallLoggingNodeExtension>(),
        private val levels: LoggingLevels = LoggingLevels(),
    ): RPCNodeExtension.Factory<CallLoggingNodeExtension> {
        override val identifier = Identifier
        override val isRequiredOnOtherSide = false

        constructor(logger: Logger, level: LoggingLevel): this(
            logger,
            LoggingLevels(level, level, level, level, level, level, level, level)
        )

        override fun create(): CallLoggingNodeExtension {
            return CallLoggingNodeExtension(logger, levels)
        }
    }
}