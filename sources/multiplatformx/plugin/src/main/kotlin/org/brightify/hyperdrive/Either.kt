package org.brightify.hyperdrive

internal sealed class Either<LEFT, RIGHT> {
    class Left<LEFT, RIGHT>(val value: LEFT): Either<LEFT, RIGHT>()
    class Right<LEFT, RIGHT>(val value: RIGHT): Either<LEFT, RIGHT>()
}