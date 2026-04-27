package kernel.providers

import kernel.foundation.Application

/**
 * Factory tipado para construir providers a partir de la aplicacion activa.
 */
typealias ProviderFactory = (Application) -> ServiceProvider
