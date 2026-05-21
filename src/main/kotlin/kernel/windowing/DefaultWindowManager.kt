package kernel.windowing

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manager opcional para ventanas hijas funcionales.
 */
class DefaultWindowManager(
    private val catalog: WindowCatalog = WindowCatalog()
) {
    private val windowsById = linkedMapOf<String, WindowDescriptor>()
    private val windowsState = MutableStateFlow<List<WindowDescriptor>>(emptyList())

    fun catalog(): WindowCatalog = catalog

    fun windows(): StateFlow<List<WindowDescriptor>> = windowsState.asStateFlow()

    fun open(
        definitionId: String,
        options: WindowLaunchOptions = WindowLaunchOptions()
    ): String {
        val instanceId = UUID.randomUUID().toString()
        val descriptor = catalog.descriptorFor(
            definitionId = definitionId,
            instanceId = instanceId,
            options = options
        )

        windowsById[instanceId] = descriptor
        publishSnapshot()
        return instanceId
    }

    fun close(instanceId: String) {
        if (windowsById.remove(instanceId) != null) {
            publishSnapshot()
        }
    }

    fun closeAll() {
        if (windowsById.isNotEmpty()) {
            windowsById.clear()
            publishSnapshot()
        }
    }

    fun find(instanceId: String): WindowDescriptor? = windowsById[instanceId]

    private fun publishSnapshot() {
        windowsState.value = windowsById.values.toList()
    }
}
