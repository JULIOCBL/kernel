package kernel.database.migrations.schema

/**
 * Constraint `UNIQUE` a nivel de tabla.
 */
internal data class UniqueConstraintDefinition(
    private val name: String,
    private val columns: List<String>
) : TableConstraintDefinition {
    /**
     * Renderiza la constraint `UNIQUE`.
     */
    override fun toSql(): String {
        return "CONSTRAINT $name UNIQUE (${columns.joinToString(", ")})"
    }
}
