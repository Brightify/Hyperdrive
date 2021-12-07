package org.brightify.hyperdrive.krpc.frame

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.SerializedPayload
import org.brightify.hyperdrive.krpc.description.ServiceCallIdentifier
import org.brightify.hyperdrive.krpc.util.RPCReference

@Serializable
@SerialName("ascension:root")
public sealed class AscensionRPCFrame: RPCFrame {
    public abstract val callReference: RPCReference

    public sealed interface Upstream {
        public val callReference: RPCReference

        public sealed interface Open {
            public val serviceCallIdentifier: ServiceCallIdentifier
        }
    }
    public sealed interface Downstream {
        public val callReference: RPCReference
    }

    @Serializable
    @SerialName("error:unknown-ref")
    public class UnknownReferenceError(override val callReference: RPCReference): AscensionRPCFrame()

    @Serializable
    @SerialName("error:protocol-violation")
    public class ProtocolViolationError(
        override val callReference: RPCReference,
        public val message: String,
    ): AscensionRPCFrame()

    public sealed class InternalProtocolError: AscensionRPCFrame() {
        public abstract val throwable: SerializableThrowable

        @Serializable
        @SerialName("error:internal:callee")
        public class Callee(
            override val callReference: RPCReference,
            override val throwable: SerializableThrowable,
        ): InternalProtocolError()

        @Serializable
        @SerialName("error:internal:caller")
        public class Caller(
            override val callReference: RPCReference,
            override val throwable: SerializableThrowable,
        ): InternalProtocolError()

        @Serializable
        @SerialName("error:internal:serializable-throwable")
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
            @SerialName("single:up:open")
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open
        }

        public sealed class Downstream: SingleCall(), AscensionRPCFrame.Downstream {
            @Serializable
            @SerialName("single:down:response")
            public class Response(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    public sealed class ColdUpstream: AscensionRPCFrame() {
        public sealed class Upstream: ColdUpstream(), AscensionRPCFrame.Upstream {
            @Serializable
            @SerialName("cold-upstream:up:open")
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            public sealed class StreamEvent: Upstream() {
                @Serializable
                @SerialName("cold-upstream:up:data")
                public class Data(public val data: SerializedPayload, override val callReference: RPCReference): StreamEvent()

                @Serializable
                @SerialName("cold-upstream:up:timeout")
                public class Timeout(public val timeoutMillis: Long, override val callReference: RPCReference): StreamEvent()
            }
        }

        public sealed class Downstream: ColdUpstream(), AscensionRPCFrame.Downstream {
            public sealed class StreamOperation: Downstream() {
                @Serializable
                @SerialName("cold-upstream:down:start")
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                @SerialName("cold-upstream:down:close")
                public class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            @SerialName("cold-upstream:down:response")
            public class Response(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()
        }
    }

    public sealed class ColdDownstream: AscensionRPCFrame() {
        public sealed class Upstream: ColdDownstream(), AscensionRPCFrame.Upstream {
            @Serializable
            @SerialName("cold-downstream:up:open")
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            public sealed class StreamOperation: Upstream() {
                @Serializable
                @SerialName("cold-downstream:up:start")
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                @SerialName("cold-downstream:up:close")
                public class Close(override val callReference: RPCReference): StreamOperation()
            }
        }

        public sealed class Downstream: ColdDownstream(), AscensionRPCFrame.Downstream {
            @Serializable
            @SerialName("cold-downstream:down:opened")
            public class Opened(override val callReference: RPCReference): Downstream()

            @Serializable
            @SerialName("cold-downstream:down:error")
            public class Error(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()

            public sealed class StreamEvent: Downstream() {
                @Serializable
                @SerialName("cold-downstream:down:data")
                public class Data(public val data: SerializedPayload, override val callReference: RPCReference): StreamEvent()

                @Serializable
                @SerialName("cold-downstream:down:timeout")
                public class Timeout(public val timeoutMillis: Long, override val callReference: RPCReference): StreamEvent()
            }
        }
    }

    public sealed class ColdBistream: AscensionRPCFrame() {
        public sealed class Upstream: ColdBistream(), AscensionRPCFrame.Upstream {
            @Serializable
            @SerialName("cold-bistream:up:open")
            public class Open(
                public val payload: SerializedPayload,
                override val serviceCallIdentifier: ServiceCallIdentifier,
                override val callReference: RPCReference,
            ): Upstream(), AscensionRPCFrame.Upstream.Open

            public sealed class StreamOperation: Upstream() {
                @Serializable
                @SerialName("cold-bistream:up:start")
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                @SerialName("cold-bistream:up:close")
                public class Close(override val callReference: RPCReference): StreamOperation()
            }

            public sealed class StreamEvent: Upstream() {
                @Serializable
                @SerialName("cold-bistream:up:data")
                public class Data(public val data: SerializedPayload, override val callReference: RPCReference): StreamEvent()

                @Serializable
                @SerialName("cold-bistream:up:timeout")
                public class Timeout(public val timeoutMillis: Long, override val callReference: RPCReference): StreamEvent()
            }
        }

        public sealed class Downstream: ColdBistream(), AscensionRPCFrame.Downstream {
            @Serializable
            @SerialName("cold-bistream:down:opened")
            public class Opened(override val callReference: RPCReference): Downstream()

            public sealed class StreamOperation: Downstream() {
                @Serializable
                @SerialName("cold-bistream:down:start")
                public class Start(override val callReference: RPCReference): StreamOperation()
                @Serializable
                @SerialName("cold-bistream:down:close")
                public class Close(override val callReference: RPCReference): StreamOperation()
            }

            @Serializable
            @SerialName("cold-bistream:down:error")
            public class Error(public val payload: SerializedPayload, override val callReference: RPCReference): Downstream()

            public sealed class StreamEvent: Downstream() {
                @Serializable
                @SerialName("cold-bistream:down:data")
                public class Data(public val data: SerializedPayload, override val callReference: RPCReference): StreamEvent()

                @Serializable
                @SerialName("cold-bistream:down:timeout")
                public class Timeout(public val timeoutMillis: Long, override val callReference: RPCReference): StreamEvent()
            }
        }
    }
}