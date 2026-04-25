package kernel.config

import kernel.env.Env

/**
 * Representa un archivo de configuracion definido como codigo Kotlin.
 *
 * Cada implementacion expone un namespace estable y devuelve un mapa con los
 * valores materializados para ese namespace, normalmente usando `Env` para leer
 * overrides o secretos.
 */
interface ConfigFile {
    val namespace: String

    fun load(env: Env): Map<String, Any?>
}
