package kernel.database.migrations.statements

/**
 * Renderiza `ALTER TABLE ... ALTER COLUMN ... SET DEFAULT`.
 */
internal data class SetColumnDefaultStatement(
    private val table: String,
    private val column: String,
    private val expression: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table ALTER COLUMN $column SET DEFAULT $expression;"
    }
}
