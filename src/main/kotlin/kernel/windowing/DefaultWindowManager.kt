package kernel.windowing

import java.util.UUID
import kernel.foundation.DerivedStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Manager opcional para ventanas hijas funcionales.
 */
class DefaultWindowManager(
    private val catalog: WindowCatalog = WindowCatalog()
) {
    private data class WindowManagerState(
        val descriptorsById: Map<String, WindowDescriptor> = emptyMap(),
        val descriptors: List<WindowDescriptor> = emptyList()
    )

    private val state = MutableStateFlow(WindowManagerState())
    private val windowsState = DerivedStateFlow(state) { current: WindowManagerState ->
        current.descriptors
    }

    fun catalog(): WindowCatalog = catalog

    fun windows(): StateFlow<List<WindowDescriptor>> = windowsState

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

        state.update { current ->
            val updated = LinkedHashMap(current.descriptorsById)
            updated[instanceId] = descriptor
            current.copy(
                descriptorsById = updated,
                descriptors = updated.values.toList()
            )
        }
        return instanceId
    }

    fun close(instanceId: String) {
        state.update { current ->
            if (!current.descriptorsById.containsKey(instanceId)) {
                return@update current
            }

            val updated = LinkedHashMap(current.descriptorsById)
            updated.remove(instanceId)
            current.copy(
                descriptorsById = updated,
                descriptors = updated.values.toList()
            )
        }
    }

    fun closeAll() {
        state.update { current ->
            if (current.descriptors.isEmpty()) {
                current
            } else {
                WindowManagerState()
            }
        }
    }

    fun find(instanceId: String): WindowDescriptor? = state.value.descriptorsById[instanceId]
}
