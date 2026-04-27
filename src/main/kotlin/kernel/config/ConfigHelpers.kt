package kernel.config

import kernel.foundation.ApplicationRuntime

/**
 * Helper global de configuracion del proceso actual.
 *
 * Debe usarse solo cuando la app ya fue bootstrappeada como singleton estable
 * del proceso. En codigo reusable o en tests, sigue siendo preferible pasar la
 * `Application` explicitamente.
 */
fun config(key: String, default: Any? = null): Any? {
    return ApplicationRuntime.current().config.get(key, default)
}
