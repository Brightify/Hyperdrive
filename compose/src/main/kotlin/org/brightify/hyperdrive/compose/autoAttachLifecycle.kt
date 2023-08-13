package org.brightify.hyperdrive.compose

import androidx.compose.runtime.*
import org.brightify.hyperdrive.LifecycleGraph
import org.brightify.hyperdrive.LifecycleRoot
import org.brightify.hyperdrive.ManageableObject

val LocalLifecycleRoot = staticCompositionLocalOf<LifecycleGraph.Root> {
    error("No LifecycleRoot!")
}

@Composable
fun LifecycleRoot(owner: Any? = null, content: @Composable LifecycleRootScope.() -> Unit) {
    val root = remember(owner) {
        LifecycleRoot(owner)
    }
    CompositionLocalProvider(
        LocalLifecycleRoot provides root,
    ) {
        val scope = rememberCoroutineScope()
        DisposableEffect(root, scope) {
            val cancelAttach = root.attach(scope)
            onDispose {
                cancelAttach.cancel()
            }
        }
        content(DefaultLifecycleRootScope)
    }
}

interface LifecycleRootScope {
    /**
     * Convenience call to [manageLifecycle].
     */
    @Composable
    fun <T: ManageableObject> T.withManagedLifecycle(): T {
        ManageLifecycle(this)
        return this
    }

    /**
     * Automatic connection management of the root view model lifecycle.
     *
     * NOTE: This needs to be done only once on the top-most view model.
     */
    @Composable
    fun ManageLifecycle(manageable: ManageableObject)
}

private object DefaultLifecycleRootScope: LifecycleRootScope {
    @Composable
    override fun ManageLifecycle(manageable: ManageableObject) {
        val root = LocalLifecycleRoot.current
        DisposableEffect(manageable) {
            root.addChild(manageable.lifecycle)
            onDispose {
                root.removeChild(manageable.lifecycle)
            }
        }
    }
}
