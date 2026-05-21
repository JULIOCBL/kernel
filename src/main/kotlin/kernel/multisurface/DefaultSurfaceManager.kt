package kernel.multisurface

import kernel.foundation.DerivedStateFlow
import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementacion en memoria para aplicaciones desktop con multiples surfaces.
 *
 * El manager es opcional: una aplicacion puede no registrarlo si no usa
 * ventanas hijas ni pantallas adicionales.
 */
class DefaultSurfaceManager(
    private val catalog: SurfaceCatalog = SurfaceCatalog(),
    private val displayDetector: () -> Int = ::defaultDisplayCount,
    private val screenBoundsProvider: (Int, Boolean) -> Rectangle? = ::defaultScreenBoundsFor,
    private var coordinator: SurfaceCoordinator = NoOpSurfaceCoordinator
) : SurfaceManager {
    private data class SurfaceManagerState(
        val displayCount: Int = 1,
        val surfacesById: Map<String, SurfaceDescriptor> = emptyMap(),
        val surfaces: List<SurfaceDescriptor> = emptyList(),
        val projectionsById: Map<String, SurfaceProjection> = emptyMap()
    )

    private val state = MutableStateFlow(
        SurfaceManagerState(displayCount = displayDetector().coerceAtLeast(1))
    )
    private val topologyState = DerivedStateFlow(state) { current: SurfaceManagerState ->
        SurfaceTopology(
            displayCount = current.displayCount,
            surfaces = current.surfaces
        )
    }

    override fun topology(): StateFlow<SurfaceTopology> = topologyState

    fun catalog(): SurfaceCatalog = catalog

    override fun projection(surfaceId: String): StateFlow<SurfaceProjection> {
        return DerivedStateFlow(state) { current: SurfaceManagerState ->
            current.projectionsById[surfaceId] ?: SurfaceProjection()
        }
    }

    override fun refreshDisplayTopology() {
        state.update { current ->
            current.copy(displayCount = displayDetector().coerceAtLeast(1))
        }
    }

    override fun displayCount(): Int = state.value.displayCount

    override fun openSurface(
        descriptor: SurfaceDescriptor,
        initialProjection: SurfaceProjection
    ): SurfaceDescriptor {
        refreshDisplayTopology()
        val normalized = descriptor.copy(
            targetDisplayIndex = normalizeDisplayIndex(descriptor.targetDisplayIndex)
        )

        state.update { current ->
            val updatedSurfacesById = LinkedHashMap(current.surfacesById)
            updatedSurfacesById[normalized.id] = normalized
            val updatedProjectionsById = LinkedHashMap(current.projectionsById)
            updatedProjectionsById[normalized.id] = initialProjection
            current.copy(
                surfacesById = updatedSurfacesById,
                surfaces = updatedSurfacesById.values.toList(),
                projectionsById = updatedProjectionsById
            )
        }

        return normalized
    }

    fun open(
        definitionId: String,
        options: SurfaceLaunchOptions = SurfaceLaunchOptions()
    ): SurfaceDescriptor {
        val plan = catalog.launchPlanFor(
            definitionId = definitionId,
            options = options
        )
        return openSurface(
            descriptor = plan.descriptor,
            initialProjection = plan.initialProjection
        )
    }

    override fun closeSurface(id: String) {
        state.update { current ->
            if (!current.surfacesById.containsKey(id) && !current.projectionsById.containsKey(id)) {
                return@update current
            }

            val updatedSurfacesById = LinkedHashMap(current.surfacesById)
            updatedSurfacesById.remove(id)
            val updatedProjectionsById = LinkedHashMap(current.projectionsById)
            updatedProjectionsById.remove(id)
            current.copy(
                surfacesById = updatedSurfacesById,
                surfaces = updatedSurfacesById.values.toList(),
                projectionsById = updatedProjectionsById
            )
        }
    }

    override fun descriptor(id: String): SurfaceDescriptor? {
        return state.value.surfacesById[id]
    }

    override fun isVisible(id: String): Boolean {
        return state.value.surfacesById[id]?.visible == true
    }

    override fun updateSurface(
        id: String,
        transform: (SurfaceDescriptor) -> SurfaceDescriptor
    ): SurfaceDescriptor? {
        var updated: SurfaceDescriptor? = null

        state.update { snapshot ->
            val current = snapshot.surfacesById[id] ?: return@update snapshot
            val transformed = transform(current)
            val normalized = transformed.copy(
                targetDisplayIndex = normalizeDisplayIndex(transformed.targetDisplayIndex)
            )
            updated = normalized

            if (!snapshot.surfacesById.containsKey(id)) {
                return@update snapshot
            }

            val updatedSurfacesById = LinkedHashMap(snapshot.surfacesById)
            updatedSurfacesById[id] = normalized
            snapshot.copy(
                surfacesById = updatedSurfacesById,
                surfaces = updatedSurfacesById.values.toList()
            )
        }
        return updated
    }

    override fun showProjection(surfaceId: String, projection: SurfaceProjection) {
        state.update { current ->
            val updatedProjectionsById = LinkedHashMap(current.projectionsById)
            updatedProjectionsById[surfaceId] = projection
            current.copy(projectionsById = updatedProjectionsById)
        }
    }

    override fun dispatchAction(surfaceId: String, action: SurfaceAction): Boolean {
        val surface = descriptor(surfaceId) ?: return false

        if (!surface.capabilities.canDispatchActions && !surface.capabilities.isInteractive()) {
            return false
        }

        return coordinator.dispatch(surfaceId, action)
    }

    override fun targetBoundsFor(surface: SurfaceDescriptor): Rectangle? {
        return screenBoundsProvider(surface.targetDisplayIndex, surface.externalDisplayPreferred)
    }

    fun setCoordinator(coordinator: SurfaceCoordinator) {
        this.coordinator = coordinator
    }

    private fun normalizeDisplayIndex(index: Int): Int {
        return index.coerceAtLeast(0).coerceAtMost(displayCount().coerceAtLeast(1) - 1)
    }

    companion object {
        private fun defaultDisplayCount(): Int {
            return try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size.coerceAtLeast(1)
            } catch (_: HeadlessException) {
                1
            } catch (_: Exception) {
                1
            }
        }

        private fun defaultScreenBoundsFor(
            preferredIndex: Int,
            externalDisplayPreferred: Boolean
        ): Rectangle? {
            val devices = try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            } catch (_: Exception) {
                return null
            }

            return when {
                devices.isEmpty() -> null
                preferredIndex in devices.indices -> devices[preferredIndex].defaultConfiguration.bounds
                externalDisplayPreferred && devices.size > 1 -> devices[1].defaultConfiguration.bounds
                else -> devices[0].defaultConfiguration.bounds
            }
        }
    }
}
