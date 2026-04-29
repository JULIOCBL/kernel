package kernel.database.statements

/**
 * Renderiza `ALTER TABLE ... RENAME TO`.
 */
internal data class RenameTableStatement(
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $from RENAME TO $to;"
    }
}
