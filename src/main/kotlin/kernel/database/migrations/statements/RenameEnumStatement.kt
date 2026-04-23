package kernel.database.migrations.statements

/**
 * Renderiza `ALTER TYPE ... RENAME TO`.
 */
internal data class RenameEnumStatement(
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TYPE $from RENAME TO $to;"
    }
}
