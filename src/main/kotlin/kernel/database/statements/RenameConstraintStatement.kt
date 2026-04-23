package kernel.database.migrations.statements

/**
 * Renderiza `ALTER TABLE ... RENAME CONSTRAINT`.
 */
internal data class RenameConstraintStatement(
    private val table: String,
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table RENAME CONSTRAINT $from TO $to;"
    }
}
