package kernel.multisurface

import java.awt.GraphicsEnvironment
import java.awt.HeadlessException
import java.awt.Rectangle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Implementacion en memoria para aplicaciones desktop con multiples surfaces.
 *
 * El manager es opcional: una aplicacion puede no registrarlo si no usa
 * ventanas hijas ni pantallas adicionales.
 */
class DefaultSurfaceManager(
    private val displayDetector: () -> Int = ::defaultDisplayCount,
    private val screenBoundsProvider: (Int, Boolean) -> Rectangle? = ::defaultScreenBoundsFor,
    private var coordinator: SurfaceCoordinator = NoOpSurfaceCoordinator
) : SurfaceManager {
    private val surfacesById = linkedMapOf<String, SurfaceDescriptor>()
    private val topologyState = MutableStateFlow(
        SurfaceTopology(displayCount = displayDetector())
    )
    private val projectionStates = linkedMapOf<String, MutableStateFlow<SurfaceProjection>>()

    override fun topology(): StateFlow<SurfaceTopology> = topologyState.asStateFlow()

    override fun projection(surfaceId: String): StateFlow<SurfaceProjection> {
        return projectionFlowFor(surfaceId).asStateFlow()
    }

    override fun refreshDisplayTopology() {
        topologyState.update { current ->
            current.copy(displayCount = displayDetector().coerceAtLeast(1))
        }
    }

    override fun displayCount(): Int = topologyState.value.displayCount

    override fun openSurface(
        descriptor: SurfaceDescriptor,
        initialProjection: SurfaceProjection
    ): SurfaceDescriptor {
        refreshDisplayTopology()
        val normalized = descriptor.copy(
            targetDisplayIndex = normalizeDisplayIndex(descriptor.targetDisplayIndex)
        )

        projectionFlowFor(normalized.id).value = initialProjection
        surfacesById[normalized.id] = normalized
        publishTopology()

        return normalized
    }

    override fun closeSurface(id: String) {
        val removedProjection = projectionStates.remove(id)
        val removedDescriptor = surfacesById.remove(id)
        if (removedProjection != null || removedDescriptor != null) {
            publishTopology()
        }
    }

    override fun descriptor(id: String): SurfaceDescriptor? {
        return surfacesById[id]
    }

    override fun isVisible(id: String): Boolean {
        return surfacesById[id]?.visible == true
    }

    override fun updateSurface(
        id: String,
        transform: (SurfaceDescriptor) -> SurfaceDescriptor
    ): SurfaceDescriptor? {
        val current = surfacesById[id] ?: return null
        val transformed = transform(current)
        val normalized = transformed.copy(
            targetDisplayIndex = normalizeDisplayIndex(transformed.targetDisplayIndex)
        )
        surfacesById[id] = normalized
        publishTopology()
        return normalized
    }

    override fun showProjection(surfaceId: String, projection: SurfaceProjection) {
        projectionFlowFor(surfaceId).value = projection
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

    private fun projectionFlowFor(surfaceId: String): MutableStateFlow<SurfaceProjection> {
        return projectionStates.getOrPut(surfaceId) { MutableStateFlow(SurfaceProjection()) }
    }

    private fun publishTopology() {
        topologyState.update { current ->
            current.copy(surfaces = surfacesById.values.toList())
        }
    }

    companion object {
        private fun defaultDisplayCount(): Int {
            return try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size.coerceAtLeast(1)
            } catch (_: HeadlessException) {
                1
            } catch (_: Throwable) {
                1
            }
        }

        private fun defaultScreenBoundsFor(
            preferredIndex: Int,
            externalDisplayPreferred: Boolean
        ): Rectangle? {
            val devices = runCatching {
                GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            }.getOrNull() ?: return null

            return when {
                devices.isEmpty() -> null
                preferredIndex in devices.indices -> devices[preferredIndex].defaultConfiguration.bounds
                externalDisplayPreferred && devices.size > 1 -> devices[1].defaultConfiguration.bounds
                else -> devices[0].defaultConfiguration.bounds
            }
        }
    }
}
