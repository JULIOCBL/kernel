package kernel.database.statements

/**
 * Renderiza `ALTER TABLE ... ALTER COLUMN ... DROP DEFAULT`.
 */
internal data class DropColumnDefaultStatement(
    private val table: String,
    private val column: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table ALTER COLUMN $column DROP DEFAULT;"
    }
}
