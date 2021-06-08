package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Contextual
import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.brightify.hyperdrive.krpc.SerializationFormat
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.protocol.RPCProtocol
import org.brightify.hyperdrive.krpc.util.RPCReference

interface RPCFrame

interface HandshakeRPCFrame: RPCFrame {

    sealed class ProtocolSelection: HandshakeRPCFrame {

        @Serializable
        class Request(val supportedProtocolVersions: List<RPCProtocol.Version>): ProtocolSelection()

        @Serializable
        sealed class Response: ProtocolSelection() {

            @Serializable
            class Success(val selectedProtocolVersion: RPCProtocol.Version): Response()
            @Serializable
            class Error(val message: String): Response()
        }
    }

    @Serializable
    sealed class Complete: HandshakeRPCFrame {
        @Serializable
        object Success: Complete()
        @Serializable
        class Error(val message: String): Complete()
    }
}

@Serializable
sealed class AscensionRPCFrame: RPCFrame {
    abstract val callReference: RPCReference

    interface Upstream {
        val callReference: RPCReference

        interface Open {
            val serviceCallIdentifier: ServiceCallIdentifier
        }
    }
    interface Downstream {
        val callReference: RPCReference
    }

    @Serializable
    class UnknownReferenceError(override val callReference: RPCReference): AscensionRPCFrame()

    @Serializable
    class ProtocolViolationError(
        override val callReference: RPCReference,
        val message: String,
    ): AscensionRPCFrame()

    sealed class InternalProtocolError: AscensionRPCFrame() {
        abstract val throwable: SerializableThrowable

        @Serializable
        class Callee(
            override val callReference: RPCReference,
            override val throwable: SerializableThrowable,
        ): InternalProtocolError()

        @Serializable
        class Caller(
            override val callReference: RPCReference,
            override val throwable: SerializableThrowable,
        ): InternalProtocolError()

        @Serializable
        class SerializableThrowable(
            val message: String? = null,
            val stacktrace: String,
            val cause: SerializableThrowable? = null,
            val suppressed: List<SerializableThrowable>,
        ) {
            constructor(throwable: Throwable): this(
                throwable.message,
                throwable.stackTraceToString(),
                throwable.cause?.let(::SerializableThrowable),
                throwable.suppressedExceptions.map(::SerializableThrowable),
            )

            fun toThrowable(): Throwable {
                // FIXME: Can we override Throwable's stack trace?
                // FIXME: Can we override Throwable's name to "simulate" the exception on the other machine?
                val throwable = Throwable(message + "\n" + stacktrace, cause?.toThrowable())
                suppressed.forEach {
                    throwable.addSuppressed(it.toThrowable())
                }
                return throwable
            }
        }
    }

    sealed class SingleCall: AscensionRPCFrame() {
        sealed class Upstream: SingleCall(), AscensionRPCFrame.Upstream {
            @Serializable
            class Open(
                val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open
        }

        sealed class Downstream: SingleCall(), AscensionRPCFrame.Downstream {
            @Serializable
            class Response(val payload: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    sealed class ColdUpstream: AscensionRPCFrame() {
        sealed class Upstream: ColdUpstream(), AscensionRPCFrame.Upstream {
            @Serializable
            class Open(
                val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            @Serializable
            class StreamEvent(val event: SerializedPayload, override val callReference: RPCReference): Upstream()
        }

        sealed class Downstream: ColdUpstream(), AscensionRPCFrame.Downstream {
            sealed class StreamOperation: Downstream() {
                @Serializable
                class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            class Response(val payload: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    sealed class ColdDownstream: AscensionRPCFrame() {
        sealed class Upstream: ColdDownstream(), AscensionRPCFrame.Upstream {
            @Serializable
            class Open(
                val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            sealed class StreamOperation: Upstream() {
                @Serializable
                class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                class Close(override val callReference: RPCReference): StreamOperation()
            }
        }

        sealed class Downstream: ColdDownstream(), AscensionRPCFrame.Downstream {
            @Serializable
            class Opened(override val callReference: RPCReference): Downstream()

            @Serializable
            class Error(val payload: SerializedPayload, override val callReference: RPCReference): Downstream()

            @Serializable
            class StreamEvent(val event: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    sealed class ColdBistream: AscensionRPCFrame() {
        sealed class Upstream: ColdBistream(), AscensionRPCFrame.Upstream {
            @Serializable
            class Open(
                val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            sealed class StreamOperation: Upstream() {
                @Serializable
                class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            class StreamEvent(val event: SerializedPayload, override val callReference: RPCReference): Upstream()
        }

        sealed class Downstream: ColdBistream(), AscensionRPCFrame.Downstream {
            @Serializable
            class Opened(override val callReference: RPCReference): Downstream()

            sealed class StreamOperation: Downstream() {
                @Serializable
                class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            class Error(val payload: SerializedPayload, override val callReference: RPCReference): Downstream()

            @Serializable
            class StreamEvent(val event: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }
}
