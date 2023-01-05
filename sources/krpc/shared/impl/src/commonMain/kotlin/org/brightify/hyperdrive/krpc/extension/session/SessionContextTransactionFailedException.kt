package org.brightify.hyperdrive.krpc.extension.session

public class SessionContextTransactionFailedException(
    public val rejectedModifications: Map<KeyDto, ContextUpdateResult.Rejected.Reason>,
    public val retryCount: Int,
): RuntimeException("Session Context Transaction was rejected after $retryCount retries. Rejected keys and reasons:\n${rejectedModifications.toList().joinToString("\n") { "\t${it.first}: ${it.second}" }}")