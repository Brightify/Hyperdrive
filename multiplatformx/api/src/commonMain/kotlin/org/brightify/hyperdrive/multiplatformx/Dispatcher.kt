package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

internal expect fun dispatcher(): CoroutineDispatcher

@Deprecated("Since there's a MainScope available in coroutines, use that.", replaceWith = ReplaceWith("MainScope()", "kotlinx.coroutines.MainScope"))
public val MultiplatformGlobalScope: CoroutineScope = CoroutineScope(dispatcher())