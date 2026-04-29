package kernel.database.schema

/**
 * Representa la llave primaria de una tabla.
 */
internal data class PrimaryKeyDefinition(
    val columns: List<String>
) {
    /**
     * Renderiza la constraint `PRIMARY KEY`.
     */
    fun toSql(): String {
        return "PRIMARY KEY (${columns.joinToString(", ")})"
    }
}
