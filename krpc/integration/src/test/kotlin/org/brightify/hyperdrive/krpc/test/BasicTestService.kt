package org.brightify.hyperdrive.krpc.test

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.brightify.hyperdrive.krpc.api.error.RPCNotFoundError
import org.brightify.hyperdrive.krpc.api.Service
import org.brightify.hyperdrive.krpc.api.Error

@Serializable
data class RPCData<T>(
    val serializer: KSerializer<T>,
    val data: T,
)

@Serializable
class IllegalArgumentError(override val message: String): RPCNotFoundError("Illegal argument: $message.")

@Service
interface BasicTestService {

    suspend fun multiplyByTwo(source: Int): Int

    suspend fun multiply(source: Int, multiplier: Int): Int

    @Error(IllegalArgumentError::class)
    suspend fun singleCallError()

    suspend fun sum(stream: @Error(IllegalArgumentError::class) Flow<Int>): Int

    suspend fun sumWithInitial(initialValue: Int, stream: Flow<Int>): Int

    suspend fun clientStreamError(stream: @Error(IllegalArgumentError::class) Flow<Unit>): IllegalArgumentError

    suspend fun timer(count: Int): @Error(IllegalArgumentError::class) Flow<Int>

    suspend fun multiplyEachByTwo(stream: Flow<Int>): Flow<Int>
}