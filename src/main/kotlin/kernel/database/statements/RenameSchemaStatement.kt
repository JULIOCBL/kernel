package kernel.database.statements

/**
 * Renderiza `ALTER SCHEMA ... RENAME TO`.
 */
internal data class RenameSchemaStatement(
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER SCHEMA $from RENAME TO $to;"
    }
}
