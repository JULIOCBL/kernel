package kernel.multisurface

import java.awt.Rectangle
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato base para manejar surfaces opcionales dentro del kernel desktop.
 */
interface SurfaceManager {
    fun topology(): StateFlow<SurfaceTopology>

    fun projection(surfaceId: String): StateFlow<SurfaceProjection>

    fun refreshDisplayTopology()

    fun displayCount(): Int

    fun openSurface(
        descriptor: SurfaceDescriptor,
        initialProjection: SurfaceProjection = SurfaceProjection()
    ): SurfaceDescriptor

    fun closeSurface(id: String)

    fun descriptor(id: String): SurfaceDescriptor?

    fun isVisible(id: String): Boolean

    fun updateSurface(id: String, transform: (SurfaceDescriptor) -> SurfaceDescriptor): SurfaceDescriptor?

    fun showProjection(surfaceId: String, projection: SurfaceProjection)

    fun dispatchAction(surfaceId: String, action: SurfaceAction): Boolean

    fun targetBoundsFor(surface: SurfaceDescriptor): Rectangle?
}
