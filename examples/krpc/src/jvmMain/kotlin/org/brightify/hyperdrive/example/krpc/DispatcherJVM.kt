package org.brightify.hyperdrive.example.krpc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi

@OptIn(InternalCoroutinesApi::class)
internal actual fun dispatcher(): CoroutineDispatcher = Dispatchers.Main