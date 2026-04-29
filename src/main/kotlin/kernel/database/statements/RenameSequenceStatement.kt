package kernel.database.statements

/**
 * Renderiza `ALTER SEQUENCE ... RENAME TO`.
 */
internal data class RenameSequenceStatement(
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER SEQUENCE $from RENAME TO $to;"
    }
}
