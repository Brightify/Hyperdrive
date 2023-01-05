package org.brightify.hyperdrive.krpc.protocol

import kotlinx.coroutines.flow.Flow
import org.brightify.hyperdrive.krpc.SerializedPayload

public sealed interface RPC {
    public sealed interface Implementation

    public sealed interface SingleCall: RPC {
        public interface Callee: SingleCall {
            public interface Implementation: RPC.Implementation {
                public suspend fun perform(payload: SerializedPayload): SerializedPayload
            }
        }

        public interface Caller: SingleCall {
            public suspend fun perform(payload: SerializedPayload): SerializedPayload
        }
    }

    public sealed interface Upstream: RPC {
        public interface Callee: Upstream {
            public interface Implementation: RPC.Implementation {
                public suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload
            }
        }

        public interface Caller: Upstream {
            public suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): SerializedPayload
        }
    }

    public sealed interface Downstream: RPC {
        public interface Callee: Downstream {
            public interface Implementation: RPC.Implementation {
                public suspend fun perform(payload: SerializedPayload): StreamOrError
            }
        }

        public interface Caller: Downstream {
            public suspend fun perform(payload: SerializedPayload): StreamOrError
        }
    }

    public sealed interface Bistream: RPC {
        public interface Callee: Bistream {
            public interface Implementation: RPC.Implementation {
                public suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): StreamOrError
            }
        }

        public interface Caller: Bistream {
            public suspend fun perform(payload: SerializedPayload, stream: Flow<SerializedPayload>): StreamOrError
        }
    }

    public sealed class StreamOrError {
        public class Stream(public val stream: Flow<SerializedPayload>): StreamOrError()
        public class Error(public val error: SerializedPayload): StreamOrError()
    }
}