package org.brightify.hyperdrive.example.krpc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.brightify.hyperdrive.Logger
import org.brightify.hyperdrive.LoggingLevel
import org.brightify.hyperdrive.multiplatformx.MultiplatformGlobalScope
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.distantFuture
import platform.Foundation.runMode
import kotlin.test.BeforeClass
import kotlin.test.Test
import kotlin.test.assertEquals

class ExampleServiceTest {

    // @Test
    fun serializeEvent() {
    }

    companion object {
        @BeforeClass
        fun setup() {
            Logger.setLevel(LoggingLevel.Trace)
        }
    }

    val logger = Logger<ExampleServiceTest>()
    fun runYieldingBlocking(block: suspend CoroutineScope.() -> Unit) {
        var finished = false
        @Suppress("DEPRECATION")
        MultiplatformGlobalScope.launch { block() }
        while (!finished) {
            logger.trace { "Will run main" }
            finished = !NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, NSDate.distantFuture)
            logger.trace { "Did run main - $finished" }
        }
    }

    // @Test
    fun runTest() {
        Logger.setLevel(LoggingLevel.Trace)

        runYieldingBlocking {
            // val client = makeClient()
            // logger.debug { "here!" }
            // assertEquals(5, client.strlen("Hello"))
            // supervisorScope {
            //     val connection = LoopbackConnection(this, 0)
            //
            //     val impl = DefaultExampleService()
            //     val registry = DefaultServiceRegistry()
            //     registry.register(ExampleService.Descriptor.describe(impl))
            //     val protocol = AscensionRPCProtocol.Factory().create(connection, registry)
            //     protocol.activate()
            //
            //     val client = ExampleService.Client(protocol) as ExampleService
            //     logger.debug { "before call" }
            //     assertEquals(5, client.strlen("Hello"))
            //
            //     protocol.close()
            // }
        }
    }

}