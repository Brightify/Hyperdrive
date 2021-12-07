package org.brightify.hyperdrive.krpc.protocol

public class StreamTimeoutException(timeoutMillis: Long): Exception("Stream not opened in time. Timeout: $timeoutMillis.")