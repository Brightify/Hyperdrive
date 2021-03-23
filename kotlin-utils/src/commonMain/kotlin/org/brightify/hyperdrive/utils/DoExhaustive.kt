package org.brightify.hyperdrive.utils

public object Do {
    public inline infix fun <reified T> exhaustive(any: T?): T? = any
}