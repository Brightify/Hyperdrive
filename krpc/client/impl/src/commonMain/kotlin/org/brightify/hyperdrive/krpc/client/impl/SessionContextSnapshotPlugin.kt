package org.brightify.hyperdrive.krpc.client.impl

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.brightify.hyperdrive.krpc.extension.SessionNodeExtension
import org.brightify.hyperdrive.krpc.session.Session

class SessionContextSnapshotPlugin: SessionNodeExtension.Plugin {
    private val listeners = mutableListOf<Listener<*>>()
    private var latestContextSnapshot: Session.Context? = null

    fun <VALUE: Any> observe(key: Session.Context.Key<VALUE>/*, includeInitialValue: Boolean = false*/): Flow<VALUE?> =
        flow {
            val channel = Channel<VALUE?>()
            val listener = FlowListener(key, channel)
            emitAll(
                channel.receiveAsFlow()
                    .onStart {
                        val contextSnapshot = latestContextSnapshot
                        if (contextSnapshot != null) {
                            emit(contextSnapshot[key]?.value)
                        }
                        registerListener(listener)
                    }
                    .onCompletion {
                        unregisterListener(listener)
                    }
            )
        }

    fun <VALUE: Any> registerListener(listener: Listener<VALUE>) {
        listeners.add(listener)
    }

    fun <VALUE: Any> unregisterListener(listener: Listener<VALUE>) {
        listeners.remove(listener)
    }

    override suspend fun onBindComplete(session: Session) {
        latestContextSnapshot = session.copyOfContext()
    }

    override suspend fun onContextChanged(session: Session, modifiedKeys: Set<Session.Context.Key<*>>) {
        val contextSnapshot = session.copyOfContext()
        latestContextSnapshot = contextSnapshot
        for (listener in listeners) {
            if (modifiedKeys.contains(listener.key)) {
                listener.notify(contextSnapshot)
            }
        }
    }

    private suspend fun <VALUE: Any> Listener<VALUE>.notify(context: Session.Context) {
        onValueChanged(context[key]?.value)
    }

    interface Listener<VALUE: Any> {
        val key: Session.Context.Key<VALUE>

        suspend fun onValueChanged(value: VALUE?)
    }

    private class FlowListener<VALUE: Any>(
        override val key: Session.Context.Key<VALUE>,
        private val channel: Channel<VALUE?>,
    ): Listener<VALUE> {
        override suspend fun onValueChanged(value: VALUE?) {
            channel.send(value)
        }
    }
}