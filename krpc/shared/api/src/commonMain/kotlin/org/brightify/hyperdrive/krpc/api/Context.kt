package org.brightify.hyperdrive.krpc.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Contextual
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

//interface SessionManager {
//
//}
//
//interface ClientSessionManager {
//    suspend fun awaitSession(): Session
//}
//
//class DefaultClientSessionManager: ClientSessionManager {
//    private val mutableActiveSession = MutableStateFlow<Session?>(null)
//
//    override suspend fun awaitSession(): Session {
//        return mutableActiveSession.mapNotNull { it }.first()
//    }
//}
//
//interface ServerSessionManager {
//
//}
//
//class DefaultServerSessionManager: ServerSessionManager {
//    private val mutableOpenSessions = MutableStateFlow<Set<Session>>(emptySet())
//}

/**
 *
 */
//public class ClientSession(
//    private val initialContext: Session.Context
//) {
//    private val context = initialContext
//
//    suspend fun transaction(block: Session.Context.Mutator.() -> Unit) {
//        val mutator = Session.Context.Mutator(context)
//        mutator.block()
//
//
//    }
//}