package kernel.config

/**
 * Almacén de configuración en memoria.
 *
 * Permite guardar valores simples o mapas anidados, y acceder a ellos mediante
 * notación de puntos. Por ejemplo: `app.name`, `database.default` o
 * `mail.drivers.smtp.host`.
 *
 * La clase protege su estado interno: los mapas recibidos se normalizan y las
 * lecturas devuelven copias defensivas cuando el valor contiene estructuras
 * mutables.
 */
class ConfigStore(initialValues: Map<String, Any?> = emptyMap()) {
    private val lock = Any()
    private val values: MutableMap<String, Any?> = linkedMapOf()

    init {
        merge(namespace = "", data = initialValues)
    }

    /**
     * Obtiene un valor de configuración usando notación de puntos.
     *
     * Si la clave no existe, devuelve `default`.
     */
    fun get(key: String, default: Any? = null): Any? = synchronized(lock) {
        val result = find(key)

        if (result.found) {
            copyValue(result.value)
        } else {
            default
        }
    }

    /**
     * Permite acceder a configuracion con estilo corto:
     * `app.config("app.name")`
     */
    operator fun invoke(key: String, default: Any? = null): Any? {
        return get(key, default)
    }

    /**
     * Establece un valor de configuración usando notación de puntos.
     *
     * Si las rutas intermedias no existen, se crean automáticamente como mapas.
     */
    fun set(key: String, value: Any?) {
        val segments = requireKeySegments(key)

        synchronized(lock) {
            val target = ensureParentMap(segments)
            target[segments.last()] = normalizeValue(value)
        }
    }

    /**
     * Fusiona un mapa de configuración dentro de un namespace.
     *
     * Si `namespace` está vacío, la fusión se realiza en la raíz. Cuando tanto
     * el valor existente como el entrante son mapas, se aplica una fusión
     * profunda para conservar claves anidadas ya existentes.
     */
    fun merge(namespace: String, data: Map<String, Any?>) {
        synchronized(lock) {
            if (namespace.isBlank()) {
                mergeInto(values, data)
                return
            }

            val segments = requireKeySegments(namespace)
            val target = ensureParentMap(segments)
            val key = segments.last()
            val existing = target[key]
            val normalized = normalizeMap(data)

            if (existing is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                mergeInto(existing as MutableMap<String, Any?>, normalized)
            } else {
                target[key] = normalized
            }
        }
    }

    /**
     * Indica si existe una clave de configuración.
     *
     * Una clave existe incluso si su valor asociado es `null`.
     */
    fun has(key: String): Boolean = synchronized(lock) {
        find(key).found
    }

    /**
     * Obtiene un valor como `String`.
     *
     * Si el valor no existe, devuelve `default`. Si existe pero no es texto, se
     * convierte usando `toString()`.
     */
    fun string(key: String, default: String = ""): String {
        return when (val value = get(key)) {
            null -> default
            is String -> value
            else -> value.toString()
        }
    }

    /**
     * Obtiene un valor como `Int`.
     *
     * Soporta valores numéricos y cadenas convertibles a entero. Si no se puede
     * convertir, devuelve `default`.
     */
    fun int(key: String, default: Int = 0): Int {
        return when (val value = get(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull() ?: default
            else -> default
        }
    }

    /**
     * Obtiene un valor como `Boolean`.
     *
     * Acepta booleanos reales, números (`0` es `false`, cualquier otro valor es
     * `true`) y cadenas comunes como `true`, `false`, `yes`, `no`, `on` y `off`.
     */
    fun bool(key: String, default: Boolean = false): Boolean {
        return when (val value = get(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> parseBoolean(value, default)
            else -> default
        }
    }

    /**
     * Obtiene un valor como mapa.
     *
     * Si la clave no existe o no apunta a un mapa, devuelve un mapa vacío. El
     * resultado es una copia segura y no expone el estado interno del store.
     */
    fun map(key: String): Map<String, Any?> = synchronized(lock) {
        val result = find(key)
        val value = result.value

        if (result.found && value is Map<*, *>) {
            copyMap(value)
        } else {
            emptyMap()
        }
    }

    /**
     * Devuelve toda la configuración almacenada.
     *
     * El mapa retornado es una copia defensiva de la configuración actual.
     */
    fun all(): Map<String, Any?> = synchronized(lock) {
        copyMap(values)
    }

    /**
     * Aplica overrides temporales y restaura el estado anterior al salir.
     *
     * Los overrides usan notacion de puntos para apuntar a claves concretas del
     * store. Mientras el bloque esta activo, cualquier lectura vera los valores
     * temporales; al finalizar, la configuracion vuelve exactamente al snapshot
     * previo.
     */
    fun <T> withTemporaryOverrides(
        overrides: Map<String, Any?>,
        block: () -> T
    ): T {
        if (overrides.isEmpty()) {
            return block()
        }

        val snapshot = synchronized(lock) {
            val previous = copyMap(values)
            applyTemporaryOverrides(overrides)
            previous
        }

        return try {
            block()
        } finally {
            synchronized(lock) {
                values.clear()
                mergeInto(values, snapshot)
            }
        }
    }

    /**
     * Busca una clave respetando la diferencia entre "no existe" y "existe con
     * valor null".
     */
    private fun find(key: String): Lookup {
        val segments = keySegmentsOrNull(key) ?: return Lookup(found = false, value = null)
        var current: Any? = values

        for (segment in segments) {
            if (current !is Map<*, *> || !current.containsKey(segment)) {
                return Lookup(found = false, value = null)
            }

            current = current[segment]
        }

        return Lookup(found = true, value = current)
    }

    /**
     * Garantiza que exista la ruta padre de una clave y devuelve el mapa donde
     * debe escribirse el último segmento.
     */
    private fun ensureParentMap(segments: List<String>): MutableMap<String, Any?> {
        var current = values

        for (segment in segments.dropLast(1)) {
            val next = current[segment]

            current = if (next is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                next as MutableMap<String, Any?>
            } else {
                linkedMapOf<String, Any?>().also { current[segment] = it }
            }
        }

        return current
    }

    /**
     * Fusiona mapas de forma profunda cuando ambos lados contienen mapas.
     */
    private fun mergeInto(target: MutableMap<String, Any?>, source: Map<String, Any?>) {
        for ((key, value) in source) {
            val existing = target[key]
            val incoming = normalizeValue(value)

            if (existing is MutableMap<*, *> && incoming is MutableMap<*, *>) {
                @Suppress("UNCHECKED_CAST")
                mergeInto(
                    existing as MutableMap<String, Any?>,
                    incoming as MutableMap<String, Any?>
                )
            } else {
                target[key] = incoming
            }
        }
    }

    /**
     * Convierte un mapa recibido desde fuera en una estructura mutable interna
     * con claves de tipo `String`.
     */
    private fun normalizeMap(source: Map<*, *>): MutableMap<String, Any?> {
        val copy = linkedMapOf<String, Any?>()

        for ((key, value) in source) {
            if (key != null) {
                copy[key.toString()] = normalizeValue(value)
            }
        }

        return copy
    }

    /**
     * Normaliza estructuras anidadas para evitar conservar referencias mutables
     * entregadas por quien llama.
     */
    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> normalizeMap(value)
            is List<*> -> value.map(::normalizeValue)
            is Set<*> -> value.mapTo(linkedSetOf(), ::normalizeValue)
            is Array<*> -> value.map(::normalizeValue)
            else -> value
        }
    }

    /**
     * Crea una copia segura de un mapa antes de devolverlo al exterior.
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
     * Crea una copia segura de un valor potencialmente compuesto.
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

    /**
     * Divide una clave por puntos y valida que no tenga segmentos vacíos.
     */
    private fun keySegmentsOrNull(key: String): List<String>? {
        val trimmed = key.trim()

        if (trimmed.isEmpty()) {
            return null
        }

        val segments = trimmed.split('.').map { it.trim() }

        return segments.takeIf { parts -> parts.all { it.isNotEmpty() } }
    }

    /**
     * Devuelve los segmentos de una clave válida o lanza una excepción clara.
     */
    private fun requireKeySegments(key: String): List<String> {
        return keySegmentsOrNull(key)
            ?: throw IllegalArgumentException("Configuration keys must not be blank or contain empty segments.")
    }

    private fun applyTemporaryOverrides(overrides: Map<String, Any?>) {
        overrides.forEach { (key, value) ->
            val segments = requireKeySegments(key)
            val target = ensureParentMap(segments)
            target[segments.last()] = normalizeValue(value)
        }
    }

    /**
     * Interpreta cadenas booleanas comunes de configuración.
     */
    private fun parseBoolean(value: String, default: Boolean): Boolean {
        return when (value.trim().lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            "false", "0", "no", "n", "off" -> false
            else -> default
        }
    }

    private data class Lookup(
        val found: Boolean,
        val value: Any?
    )
}
