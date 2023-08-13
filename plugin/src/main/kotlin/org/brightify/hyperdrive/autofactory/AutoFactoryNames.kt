package org.brightify.hyperdrive.autofactory

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object AutoFactoryNames {
    object Annotation {
        val autoFactory = FqName("org.brightify.hyperdrive.AutoFactory")
        val provided = FqName("org.brightify.hyperdrive.Provided")
    }

    val factory = Name.identifier("Factory")
    val createFun = Name.identifier("create")
}
