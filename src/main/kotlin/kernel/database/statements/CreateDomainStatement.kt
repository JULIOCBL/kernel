package kernel.database.migrations.statements

/**
 * Renderiza `CREATE DOMAIN`.
 */
internal data class CreateDomainStatement(
    private val name: String,
    private val type: String,
    private val notNull: Boolean,
    private val defaultExpression: String?,
    private val checkExpression: String?
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val clauses = listOfNotNull(
            if (notNull) "NOT NULL" else null,
            defaultExpression?.let { expression -> "DEFAULT $expression" },
            checkExpression?.let { expression -> "CHECK ($expression)" }
        )
        val clauseSql = clauses.takeIf { values -> values.isNotEmpty() }
            ?.joinToString(" ", prefix = " ")
            .orEmpty()

        return "CREATE DOMAIN $name AS $type$clauseSql;"
    }
}
