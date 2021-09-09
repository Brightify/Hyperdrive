package org.brightify.hyperdrive.krpc.client.impl

import platform.Foundation.NSError

class NSErrorThrowable(val error: NSError): Throwable(error.description)