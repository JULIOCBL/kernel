package kernel.database.migrations.schema

/**
 * Constraint PostgreSQL `EXCLUDE` con metodo de indice y elementos arbitrarios.
 */
internal data class ExcludeConstraintDefinition(
    private val name: String,
    private val using: String,
    private val elements: List<String>,
    private val where: String?
) : TableConstraintDefinition {
    /**
     * Renderiza la constraint `EXCLUDE`, incluyendo `WHERE` cuando existe.
     */
    override fun toSql(): String {
        val whereClause = where?.let { expression -> " WHERE ($expression)" }.orEmpty()

        return "CONSTRAINT $name EXCLUDE USING $using (${elements.joinToString(", ")})$whereClause"
    }
}
