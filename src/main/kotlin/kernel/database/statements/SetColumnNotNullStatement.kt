package kernel.database.statements

/**
 * Renderiza `ALTER TABLE ... ALTER COLUMN ... SET NOT NULL`.
 */
internal data class SetColumnNotNullStatement(
    private val table: String,
    private val column: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table ALTER COLUMN $column SET NOT NULL;"
    }
}
