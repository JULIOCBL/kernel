package kernel.http.server

object JsonCodec {
    fun encode(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> encodeObject(value)
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { item ->
                encode(item)
            }
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { item ->
                encode(item)
            }
            else -> "\"${escape(value.toString())}\""
        }
    }

    private fun encodeObject(map: Map<*, *>): String {
        return map.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key.toString())}\":${encode(value)}"
        }
    }

    private fun escape(value: String): String {
        return buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
    }

    fun decodeObject(value: String): Map<String, String> {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed == "{}") {
            return emptyMap()
        }

        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "Solo se soportan objetos JSON planos en este laboratorio."
        }

        val body = trimmed.removePrefix("{").removeSuffix("}").trim()
        if (body.isBlank()) {
            return emptyMap()
        }

        return body.split(',')
            .map(String::trim)
            .associate { pair ->
                val parts = pair.split(':', limit = 2)
                require(parts.size == 2) {
                    "Par JSON invalido: `$pair`."
                }
                val key = unquote(parts[0].trim())
                val rawValue = parts[1].trim()
                val normalizedValue = if (rawValue == "null") {
                    ""
                } else {
                    unquote(rawValue)
                }
                key to normalizedValue
            }
    }

    private fun unquote(value: String): String {
        val normalized = value.trim()
        return if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized
                .removePrefix("\"")
                .removeSuffix("\"")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        } else {
            normalized
        }
    }
}
