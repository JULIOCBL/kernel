package kernel.http

class ValidatedInput(
    private val values: Map<String, Any?>
) {
    fun all(): Map<String, Any?> = values.toMap()

    fun only(vararg keys: String): Map<String, Any?> {
        val requested = keys.toSet()
        return values.filterKeys { it in requested }
    }

    fun except(vararg keys: String): Map<String, Any?> {
        val ignored = keys.toSet()
        return values.filterKeys { it !in ignored }
    }
}
