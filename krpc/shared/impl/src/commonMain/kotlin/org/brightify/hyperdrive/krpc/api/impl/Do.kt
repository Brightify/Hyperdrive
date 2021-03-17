package org.brightify.hyperdrive.krpc.api.impl

object Do {
    inline infix fun <reified T> exhaustive(any: T?) = any
}