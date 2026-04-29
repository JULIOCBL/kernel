package kernel.database.statements

/**
 * Renderiza `ALTER TABLE ... ALTER COLUMN ... TYPE`.
 */
internal data class AlterColumnTypeStatement(
    private val table: String,
    private val column: String,
    private val type: String,
    private val usingExpression: String?
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val usingClause = usingExpression?.let { expression ->
            " USING $expression"
        }.orEmpty()

        return "ALTER TABLE $table ALTER COLUMN $column TYPE $type$usingClause;"
    }
}
