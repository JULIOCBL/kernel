package kernel.multisurface

import kernel.foundation.Application
import kernel.foundation.app

fun Application.surfaceCatalogOrNull(): SurfaceCatalog? {
    return config.get("services.surfaces.catalog") as? SurfaceCatalog
}

fun Application.surfaceManagerOrNull(): SurfaceManager? {
    return config.get("services.surfaces.manager") as? SurfaceManager
}

fun Application.surfaceCoordinatorOrNull(): SurfaceCoordinator? {
    return config.get("services.surfaces.coordinator") as? SurfaceCoordinator
}

fun surfaceManager(): SurfaceManager {
    return app().surfaceManagerOrNull()
        ?: error("SurfaceManager no esta registrado en services.surfaces.manager.")
}

fun surfaceCatalog(): SurfaceCatalog {
    return app().surfaceCatalogOrNull()
        ?: error("SurfaceCatalog no esta registrado en services.surfaces.catalog.")
}

fun surfaceCoordinator(): SurfaceCoordinator {
    return app().surfaceCoordinatorOrNull()
        ?: error("SurfaceCoordinator no esta registrado en services.surfaces.coordinator.")
}
