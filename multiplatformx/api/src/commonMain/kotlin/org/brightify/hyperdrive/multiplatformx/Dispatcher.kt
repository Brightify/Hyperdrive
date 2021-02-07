package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal expect fun dispatcher(): CoroutineDispatcher

val MultiplatformGlobalScope = CoroutineScope(dispatcher())