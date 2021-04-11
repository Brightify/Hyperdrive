package org.brightify.hyperdrive.multiplatformx

import kotlinx.coroutines.flow.Flow

public interface ManageableViewModel {
    public val observeObjectWillChange: Flow<Unit>

    public val lifecycle: Lifecycle?
}