package org.brightify.hyperdrive.krpc.api

import kotlin.reflect.KClass

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
annotation class Error(vararg val cls: KClass<out RPCError>)