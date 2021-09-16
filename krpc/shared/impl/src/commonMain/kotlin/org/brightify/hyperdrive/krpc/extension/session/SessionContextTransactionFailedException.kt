package org.brightify.hyperdrive.krpc.extension.session

import org.brightify.hyperdrive.krpc.extension.ContextUpdateResult
import org.brightify.hyperdrive.krpc.extension.KeyDto

public class SessionContextTransactionFailedException(
    val rejectedModifications: Map<KeyDto, ContextUpdateResult.Rejected.Reason>,
    val retryCount: Int,
): RuntimeException("Session Context Transaction was rejected after $retryCount retries. Rejected keys and reasons:\n${rejectedModifications.toList().joinToString("\n") { "\t${it.first}: ${it.second}" }}")