package kernel.database.migrations.statements

/**
 * Renderiza `ALTER TABLE ... RENAME COLUMN`.
 */
internal data class RenameColumnStatement(
    private val table: String,
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table RENAME COLUMN $from TO $to;"
    }
}
