package org.brightify.hyperdrive

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi

@OptIn(InternalCoroutinesApi::class)
internal actual fun dispatcher(): CoroutineDispatcher = Dispatchers.Main
