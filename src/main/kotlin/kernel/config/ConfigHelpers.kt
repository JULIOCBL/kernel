package kernel.config

import kernel.foundation.ApplicationContext

/**
 * Helper global estilo Laravel.
 *
 * Ejemplo:
 * `config("app.name")`
 */
fun config(key: String, default: Any? = null): Any? {
    return ApplicationContext.current().config.get(key, default)
}
