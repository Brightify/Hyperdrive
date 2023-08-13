package org.brightify.hyperdrive.impl

internal class BlockCancellationToken(onCancel: () -> Unit): BaseCancellationToken(onCancel)
