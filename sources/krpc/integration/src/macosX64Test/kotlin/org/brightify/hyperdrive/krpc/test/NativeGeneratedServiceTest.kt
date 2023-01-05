package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.runBlocking
import org.brightify.hyperdrive.krpc.RPCConnection
import org.brightify.hyperdrive.krpc.client.RPCClientConnector
import org.brightify.hyperdrive.krpc.client.impl.KRPCClient
import org.brightify.hyperdrive.krpc.impl.DefaultServiceRegistry
import kotlin.test.Test

class NativeGeneratedServiceTest {

    @Test
    fun testError() = runBlocking {
        println("what what")
        val serviceRegistry = DefaultServiceRegistry()
        val loopbackConnection = LoopbackConnection(this)
        val krpcClient = KRPCClient(
            object: RPCClientConnector {
                override suspend fun withConnection(block: suspend RPCConnection.() -> Unit) {
                    block(loopbackConnection)
                }

                override fun isConnectionCloseException(throwable: Throwable): Boolean {
                    throwable.printStackTrace()
                    return false
                }
            },
            serviceRegistry,
            this
        )

        val client = BasicTestService.Client(krpcClient) as BasicTestService

        try {
            println("single error")
            client.singleCallError()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

}