package org.brightify.hyperdrive.client.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.DeserializationStrategy
import org.brightify.hyperdrive.krpc.api.error.RPCErrorSerializer

interface PendingRPC<INCOMING> {
    val stateManager: StateManager

    val deserializationStrategy: DeserializationStrategy<INCOMING>

    val errorSerializer: RPCErrorSerializer

    suspend fun accept(data: INCOMING)

    suspend fun reject(throwable: Throwable)

    suspend fun close(throwable: Throwable?)

    enum class State {
        Closed,
        Busy,
        Ready,
    }

    class StateManager {
        var state = State.Closed
            private set

        private val observers: MutableMap<State, MutableSet<CompletableDeferred<Unit>>> = mutableMapOf()

        fun setOpened() {
            require(state == State.Closed) { "Required to be closed, was $state" }

            state = State.Ready

            notifyStateChanged()
        }

        fun setBusy() {
            require(state == State.Ready)

            state = State.Busy

            notifyStateChanged()
        }

        fun setReady() {
            require(state == State.Busy)

            state = State.Ready

            notifyStateChanged()
        }

        suspend fun await(state: State) {
            if (this.state == state) {
                return
            } else {
                val observer = CompletableDeferred<Unit>()
                observers.getOrPut(state, ::mutableSetOf).add(observer)
                observer.await()
            }
        }

        private fun notifyStateChanged() {
            val state = this.state
            val stateObservers = observers.remove(state) ?: return

            for (observer in stateObservers) {
                observer.complete(Unit)
            }
        }
    }
}