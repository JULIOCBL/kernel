package kernel.config

/**
 * Contrato base para cargar configuración desde cualquier fuente.
 *
 * La intención es mantener la capa de configuración desacoplada del origen de
 * datos. Más adelante se pueden agregar loaders para archivos, JSON, YAML,
 * properties u otras fuentes sin cambiar `ConfigStore`.
 */
interface ConfigLoader {
    /**
     * Carga y devuelve los datos de configuración.
     *
     * El mapa retornado debe representar la configuración ya materializada,
     * normalmente con mapas anidados cuando existan estructuras jerárquicas.
     */
    fun load(): Map<String, Any?>
}

/**
 * Loader simple para configuración ya construida en memoria como `Map`.
 *
 * Es útil para tests, configuración programática o como primer paso antes de
 * implementar loaders basados en archivos.
 */
class MapConfigLoader(private val data: Map<String, Any?>) : ConfigLoader {
    /**
     * Devuelve una copia defensiva de los datos originales.
     */
    override fun load(): Map<String, Any?> = copyMap(data)

    /**
     * Copia un mapa externo normalizando sus claves a `String`.
     */
    private fun copyMap(source: Map<*, *>): Map<String, Any?> {
        val copy = linkedMapOf<String, Any?>()

        for ((key, value) in source) {
            if (key != null) {
                copy[key.toString()] = copyValue(value)
            }
        }

        return copy.toMap()
    }

    /**
     * Copia estructuras anidadas para evitar exponer referencias mutables.
     */
    private fun copyValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> copyMap(value)
            is List<*> -> value.map(::copyValue)
            is Set<*> -> value.mapTo(linkedSetOf(), ::copyValue).toSet()
            is Array<*> -> value.map(::copyValue)
            else -> value
        }
    }
}
