package kernel.windowing

import kernel.foundation.Application
import kernel.foundation.app

fun Application.windowManagerOrNull(): DefaultWindowManager? {
    return config.get("services.windows.manager") as? DefaultWindowManager
}

fun Application.windowCatalogOrNull(): WindowCatalog? {
    return config.get("services.windows.catalog") as? WindowCatalog
}

fun windowManager(): DefaultWindowManager {
    return app().windowManagerOrNull()
        ?: error("DefaultWindowManager no esta registrado en services.windows.manager.")
}

fun windowCatalog(): WindowCatalog {
    return app().windowCatalogOrNull()
        ?: error("WindowCatalog no esta registrado en services.windows.catalog.")
}
