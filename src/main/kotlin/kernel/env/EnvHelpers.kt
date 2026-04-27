package kernel.env

import kernel.foundation.app

/**
 * Helper global para leer variables de entorno de la aplicacion bootstrappeada.
 */
fun env(key: String, default: String? = null): String? {
    return app().env.get(key, default)
}
