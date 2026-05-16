package kernel.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

object SessionSerializer {
    fun serialize(values: Map<String, Any?>): ByteArray {
        val payload = SessionPayload(normalizeMap(values))

        return ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { stream ->
                stream.writeObject(payload)
                stream.flush()
            }
            output.toByteArray()
        }
    }

    fun deserialize(bytes: ByteArray): Map<String, Any?> {
        val payload = ByteArrayInputStream(bytes).use { input ->
            ObjectInputStream(input).use { stream ->
                stream.readObject() as SessionPayload
            }
        }

        return payload.values
    }

    fun toJson(values: Map<String, Any?>): String {
        return renderJson(normalizeValue(values), depth = 0)
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> normalizeMap(value)
            is Iterable<*> -> value.map(::normalizeValue)
            is Array<*> -> value.map(::normalizeValue)
            is BooleanArray -> value.map(Boolean::toString).map { it == "true" }
            is ByteArray -> value.map(Byte::toInt)
            is ShortArray -> value.map(Short::toInt)
            is IntArray -> value.toList()
            is LongArray -> value.toList()
            is FloatArray -> value.toList()
            is DoubleArray -> value.toList()
            is CharArray -> value.map(Char::toString)
            is Enum<*> -> value.name
            is Serializable -> value
            else -> value.toString()
        }
    }

    private fun normalizeMap(source: Map<*, *>): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            source.forEach { (key, value) ->
                if (key != null) {
                    put(key.toString(), normalizeValue(value))
                }
            }
        }
    }

    private fun renderJson(value: Any?, depth: Int): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escapeJson(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> renderJsonObject(value, depth)
            is Iterable<*> -> renderJsonArray(value.toList(), depth)
            else -> "\"${escapeJson(value.toString())}\""
        }
    }

    private fun renderJsonObject(value: Map<*, *>, depth: Int): String {
        if (value.isEmpty()) {
            return "{}"
        }

        val indent = "  ".repeat(depth)
        val nestedIndent = "  ".repeat(depth + 1)
        val entries = value.entries.joinToString(",\n") { (key, nestedValue) ->
            "$nestedIndent\"${escapeJson(key.toString())}\": ${renderJson(nestedValue, depth + 1)}"
        }

        return "{\n$entries\n$indent}"
    }

    private fun renderJsonArray(values: List<*>, depth: Int): String {
        if (values.isEmpty()) {
            return "[]"
        }

        val indent = "  ".repeat(depth)
        val nestedIndent = "  ".repeat(depth + 1)
        val entries = values.joinToString(",\n") { nestedValue ->
            "$nestedIndent${renderJson(nestedValue, depth + 1)}"
        }

        return "[\n$entries\n$indent]"
    }

    private fun escapeJson(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (character.code < 0x20) {
                            append("\\u%04x".format(character.code))
                        } else {
                            append(character)
                        }
                    }
                }
            }
        }
    }

    private data class SessionPayload(
        val values: Map<String, Any?>
    ) : Serializable
}
