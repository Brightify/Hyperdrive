package org.brightify.hyperdrive.multiplatformx.impl

internal class BlockCancellationToken(onCancel: () -> Unit): BaseCancellationToken(onCancel)