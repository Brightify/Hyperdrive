package org.brightify.hyperdrive.krpc.application

public class HandshakeFailedException(public val rpcMesage: String): Exception("Handshake has failed: $rpcMesage")