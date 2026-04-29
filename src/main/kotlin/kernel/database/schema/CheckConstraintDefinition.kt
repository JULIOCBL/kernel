package kernel.database.schema

/**
 * Constraint `CHECK` con nombre explicito.
 */
internal data class CheckConstraintDefinition(
    private val name: String,
    private val expression: String
) : TableConstraintDefinition {
    /**
     * Renderiza la constraint `CHECK`.
     */
    override fun toSql(): String {
        return "CONSTRAINT $name CHECK ($expression)"
    }
}
