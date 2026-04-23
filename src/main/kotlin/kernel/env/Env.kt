package kernel.env

/**
 * Lector cacheado de variables de entorno.
 *
 * Combina valores cargados desde un archivo `.env` con los valores disponibles
 * en `System.getenv()`. Las variables reales del sistema tienen prioridad
 * sobre las cargadas desde archivo, lo que permite sobrescribir configuración
 * local en producción, CI o contenedores.
 */
class Env(
    loadedValues: Map<String, String> = emptyMap(),
    systemValues: Map<String, String> = System.getenv()
) {
    private val values: Map<String, String> = linkedMapOf<String, String>()
        .also {
            it.putAll(normalize(loadedValues))
            it.putAll(normalize(systemValues))
        }
        .toMap()

    /**
     * Obtiene una variable de entorno por clave.
     *
     * Si no existe, devuelve `default`.
     */
    fun get(key: String, default: String? = null): String? {
        return values[normalizeKey(key)] ?: default
    }

    /**
     * Indica si existe una variable de entorno.
     */
    fun has(key: String): Boolean {
        return values.containsKey(normalizeKey(key))
    }

    /**
     * Obtiene una variable como `String`.
     *
     * Si la variable no existe, devuelve `default`.
     */
    fun string(key: String, default: String = ""): String {
        return get(key) ?: default
    }

    /**
     * Obtiene una variable como `Int`.
     *
     * Si la variable no existe o no puede convertirse a entero, devuelve
     * `default`.
     */
    fun int(key: String, default: Int = 0): Int {
        return get(key)?.trim()?.toIntOrNull() ?: default
    }

    /**
     * Obtiene una variable como `Boolean`.
     *
     * Acepta valores comunes como `true`, `false`, `1`, `0`, `yes`, `no`,
     * `on` y `off`. Si no se reconoce el valor, devuelve `default`.
     */
    fun bool(key: String, default: Boolean = false): Boolean {
        return when (get(key)?.trim()?.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> default
        }
    }

    /**
     * Devuelve todas las variables disponibles para esta instancia.
     *
     * El resultado es una copia segura del cache interno.
     */
    fun all(): Map<String, String> {
        return values.toMap()
    }

    /**
     * Limpia claves vacías y aplica normalización consistente a las fuentes.
     */
    private fun normalize(source: Map<String, String>): Map<String, String> {
        val normalized = linkedMapOf<String, String>()

        for ((key, value) in source) {
            val normalizedKey = normalizeKey(key)

            if (normalizedKey.isNotEmpty()) {
                normalized[normalizedKey] = value
            }
        }

        return normalized
    }

    /**
     * Normaliza una clave de entorno antes de guardarla o consultarla.
     */
    private fun normalizeKey(key: String): String {
        return key.trim()
    }
}
