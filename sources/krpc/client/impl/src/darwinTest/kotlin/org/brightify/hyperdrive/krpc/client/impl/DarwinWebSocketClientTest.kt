package org.brightify.hyperdrive.krpc.client.impl

import co.touchlab.stately.ensureNeverFrozen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.brightify.hyperdrive.krpc.SerializedFrame
import org.brightify.hyperdrive.krpc.impl.JsonCombinedSerializer
import org.brightify.hyperdrive.krpc.impl.SerializerRegistry
import platform.Foundation.NSURL
import kotlin.coroutines.coroutineContext
import kotlin.native.concurrent.Worker
import kotlin.test.BeforeTest
import kotlin.test.Test

class DarwinWebSocketClientTest {

    @BeforeTest
    fun setUp() {
    }

    // @Test
    fun testConnectionSuccessful() {
        val mainThreadSurrogate = newSingleThreadContext("UI Thread")

        runBlocking(mainThreadSurrogate) {
            val mainScope = this
            println("We not started")
            val connector = DarwinWebSocketClient(NSURL.URLWithString("wss://qaevntly.brightify.org/main-api")!!)
            val client = KRPCClient(connector, runScope = this, serializerRegistry = SerializerRegistry(JsonCombinedSerializer.Factory())).apply { ensureNeverFrozen() }
            client.run()
        }
    }

}

@OptIn(ExperimentalStdlibApi::class)
suspend fun <T> runWithoutFreezingContext(block: suspend () -> T): T {
    val result = CompletableDeferred<T>()
    val dispatcher = requireNotNull(coroutineContext[CoroutineDispatcher]) { "CoroutineDispatcher is required to run isolated." }
    val job = SupervisorJob()
    val newScope = CoroutineScope(job + dispatcher)

    coroutineContext.job.invokeOnCompletion {
        newScope.cancel(it as? CancellationException)
    }

    return try {
        newScope.launch {
            try {
                result.complete(block())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        result.await()
    } finally {
        newScope.cancel()
    }
}