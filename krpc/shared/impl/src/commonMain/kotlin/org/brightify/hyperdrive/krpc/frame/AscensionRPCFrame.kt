package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.util.RPCReference

@Serializable
public sealed class AscensionRPCFrame: RPCFrame {
    public abstract val callReference: RPCReference

    public interface Upstream {
        public val callReference: RPCReference

        public interface Open {
            public val serviceCallIdentifier: ServiceCallIdentifier
        }
    }
    public interface Downstream {
        public val callReference: RPCReference
    }

    @Serializable
    public class UnknownReferenceError(override val callReference: RPCReference): AscensionRPCFrame()

    @Serializable
    public class ProtocolViolationError(
        override val callReference: RPCReference,
        public val message: String,
    ): AscensionRPCFrame()

    public sealed class InternalProtocolError: AscensionRPCFrame() {
        public abstract val throwable: SerializableThrowable

        @Serializable
        public class Callee(
            override val callReference: RPCReference,
            override val throwable: SerializableThrowable,
        ): InternalProtocolError()

        @Serializable
        public class Caller(
            override val callReference: RPCReference,
            override val throwable: SerializableThrowable,
        ): InternalProtocolError()

        @Serializable
        public class SerializableThrowable(
            public val message: String? = null,
            public val stacktrace: String,
            public val cause: SerializableThrowable? = null,
            public val suppressed: List<SerializableThrowable> = emptyList(),
        ) {
            public constructor(throwable: Throwable): this(
                throwable.message,
                throwable.stackTraceToString(),
                throwable.cause?.let(::SerializableThrowable),
                throwable.suppressedExceptions.map(::SerializableThrowable),
            )

            public fun toThrowable(): Throwable {
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

    public sealed class SingleCall: AscensionRPCFrame() {
        public sealed class Upstream: SingleCall(), AscensionRPCFrame.Upstream {
            @Serializable
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open
        }

        public sealed class Downstream: SingleCall(), AscensionRPCFrame.Downstream {
            @Serializable
            public class Response(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    public sealed class ColdUpstream: AscensionRPCFrame() {
        public sealed class Upstream: ColdUpstream(), AscensionRPCFrame.Upstream {
            @Serializable
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            @Serializable
            public class StreamEvent(public val event: SerializedPayload, override val callReference: RPCReference): Upstream()
        }

        public sealed class Downstream: ColdUpstream(), AscensionRPCFrame.Downstream {
            public sealed class StreamOperation: Downstream() {
                @Serializable
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                public class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            public class Response(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    public sealed class ColdDownstream: AscensionRPCFrame() {
        public sealed class Upstream: ColdDownstream(), AscensionRPCFrame.Upstream {
            @Serializable
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            public sealed class StreamOperation: Upstream() {
                @Serializable
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                public class Close(override val callReference: RPCReference): StreamOperation()
            }
        }

        public sealed class Downstream: ColdDownstream(), AscensionRPCFrame.Downstream {
            @Serializable
            public class Opened(override val callReference: RPCReference): Downstream()

            @Serializable
            public class Error(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()

            @Serializable
            public class StreamEvent(public val event: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    public sealed class ColdBistream: AscensionRPCFrame() {
        public sealed class Upstream: ColdBistream(), AscensionRPCFrame.Upstream {
            @Serializable
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            public sealed class StreamOperation: Upstream() {
                @Serializable
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                public class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            public class StreamEvent(public val event: SerializedPayload, override val callReference: RPCReference): Upstream()
        }

        public sealed class Downstream: ColdBistream(), AscensionRPCFrame.Downstream {
            @Serializable
            public class Opened(override val callReference: RPCReference): Downstream()

            public sealed class StreamOperation: Downstream() {
                @Serializable
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                public class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            public class Error(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()

            @Serializable
            public class StreamEvent(public val event: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }
}