package kernel.providers

import kernel.foundation.Application
import kernel.multisurface.DefaultSurfaceManager
import kernel.multisurface.NoOpSurfaceCoordinator
import kernel.multisurface.SurfaceCoordinator
import kernel.multisurface.SurfaceManager

/**
 * Provider opcional para aplicaciones que quieran habilitar surfaces.
 */
class SurfaceServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        val coordinator = app.config.get("services.surfaces.coordinator") as? SurfaceCoordinator
            ?: NoOpSurfaceCoordinator.also {
                app.config.set("services.surfaces.coordinator", it)
            }

        val existingManager = app.config.get("services.surfaces.manager") as? SurfaceManager
        if (existingManager == null) {
            app.config.set(
                "services.surfaces.manager",
                DefaultSurfaceManager(coordinator = coordinator)
            )
        }
    }
}
