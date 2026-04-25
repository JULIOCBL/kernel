package kernel.env

import kernel.foundation.app

/**
 * Helper global estilo Laravel para leer variables de entorno.
 *
 * Ejemplo:
 * `env("APP_NAME")`
 */
fun env(key: String, default: String? = null): String? {
    return app().env.get(key, default)
}
