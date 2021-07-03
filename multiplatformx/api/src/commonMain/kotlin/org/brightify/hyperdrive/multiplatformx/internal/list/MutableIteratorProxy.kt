package org.brightify.hyperdrive.multiplatformx.internal.list

import org.brightify.hyperdrive.multiplatformx.ManageableViewModel

internal class MutableIteratorProxy<T>(
    private val objectWillChangeTrigger: ManageableViewModel.ObjectWillChangeTrigger,
    private val iterator: MutableIterator<T>,
): MutableIterator<T>, Iterator<T> by iterator {
    override fun remove() {
        objectWillChangeTrigger.notifyObjectWillChange()
        iterator.remove()
    }
}