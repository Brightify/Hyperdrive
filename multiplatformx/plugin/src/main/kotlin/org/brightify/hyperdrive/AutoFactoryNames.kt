package org.brightify.hyperdrive

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object AutoFactoryNames {
    object Annotation {
        val autoFactory = FqName("org.brightify.hyperdrive.multiplatformx.AutoFactory")
        val provided = FqName("org.brightify.hyperdrive.multiplatformx.Provided")
    }

    val factory = Name.identifier("Factory")
    val createFun = Name.identifier("create")
}