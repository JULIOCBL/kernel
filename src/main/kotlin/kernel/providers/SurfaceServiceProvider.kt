package kernel.providers

import kernel.foundation.Application
import kernel.multisurface.DefaultSurfaceManager
import kernel.multisurface.NoOpSurfaceCoordinator
import kernel.multisurface.SurfaceCatalog
import kernel.multisurface.SurfaceCoordinator
import kernel.multisurface.SurfaceManager

/**
 * Provider opcional para aplicaciones que quieran habilitar surfaces.
 */
class SurfaceServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        val catalog = app.config.get("services.surfaces.catalog") as? SurfaceCatalog
            ?: SurfaceCatalog().also {
                app.config.set("services.surfaces.catalog", it)
            }

        val coordinator = app.config.get("services.surfaces.coordinator") as? SurfaceCoordinator
            ?: NoOpSurfaceCoordinator.also {
                app.config.set("services.surfaces.coordinator", it)
            }

        val existingManager = app.config.get("services.surfaces.manager") as? SurfaceManager
        if (existingManager == null) {
            app.config.set(
                "services.surfaces.manager",
                DefaultSurfaceManager(
                    catalog = catalog,
                    coordinator = coordinator
                )
            )
        }
    }
}
