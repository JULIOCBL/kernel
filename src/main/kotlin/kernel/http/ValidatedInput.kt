package kernel.http

import kotlin.reflect.KClass

class ValidatedInput(
    private val values: Map<String, Any?>
) {
    fun all(): Map<String, Any?> = values.toMap()

    fun keys(): List<String> = values.keys.toList()

    operator fun get(key: String): Any? = values[key]

    fun getValue(key: String): Any? {
        return values.getValue(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> typed(key: String): T? = values[key] as? T

    fun string(key: String, default: String? = null): String? {
        val value = values[key] ?: return default
        return when (value) {
            is String -> value
            else -> value.toString()
        }
    }

    fun int(key: String, default: Int? = null): Int? {
        val value = values[key] ?: return default
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    fun long(key: String, default: Long? = null): Long? {
        val value = values[key] ?: return default
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: default
            else -> default
        }
    }

    fun double(key: String, default: Double? = null): Double? {
        val value = values[key] ?: return default
        return when (value) {
            is Double -> value
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    fun boolean(key: String, default: Boolean? = null): Boolean? {
        val value = values[key] ?: return default
        return when (value) {
            is Boolean -> value
            is String -> parseBoolean(value) ?: default
            else -> default
        }
    }

    fun list(key: String): List<Any?> {
        val value = values[key] ?: return emptyList()
        return when (value) {
            is List<*> -> value
            is Array<*> -> value.toList()
            is String -> value.split(',').map(String::trim).filter(String::isNotBlank)
            else -> emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun map(key: String): Map<String, Any?> {
        val value = values[key] ?: return emptyMap()
        return when (value) {
            is Map<*, *> -> value.entries
                .filter { it.key != null }
                .associate { it.key.toString() to it.value }
            else -> emptyMap()
        }
    }

    fun <E : Enum<E>> enum(
        key: String,
        enumType: KClass<E>,
        default: E? = null
    ): E? {
        val value = values[key] ?: return default
        return when (value) {
            is Enum<*> -> if (enumType.java.isInstance(value)) enumType.java.cast(value) else default
            is String -> enumType.java.enumConstants.firstOrNull { candidate ->
                candidate.name.equals(value.trim(), ignoreCase = true)
            } ?: default
            else -> default
        }
    }

    fun file(key: String): UploadedFile? = values[key] as? UploadedFile

    fun has(key: String): Boolean = values.containsKey(key)

    fun only(vararg keys: String): Map<String, Any?> {
        val requested = keys.toSet()
        return values.filterKeys { it in requested }
    }

    fun except(vararg keys: String): Map<String, Any?> {
        val ignored = keys.toSet()
        return values.filterKeys { it !in ignored }
    }

    private fun parseBoolean(value: String): Boolean? {
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }
}
