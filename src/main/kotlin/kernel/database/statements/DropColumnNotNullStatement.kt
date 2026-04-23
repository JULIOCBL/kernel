package kernel.database.migrations.statements

/**
 * Renderiza `ALTER TABLE ... ALTER COLUMN ... DROP NOT NULL`.
 */
internal data class DropColumnNotNullStatement(
    private val table: String,
    private val column: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table ALTER COLUMN $column DROP NOT NULL;"
    }
}
