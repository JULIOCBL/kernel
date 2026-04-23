package kernel.database.migrations.statements

/**
 * Renderiza `DROP TRIGGER`.
 */
internal data class DropTriggerStatement(
    private val name: String,
    private val table: String,
    private val ifExists: Boolean,
    private val cascade: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val existenceClause = if (ifExists) {
            " IF EXISTS"
        } else {
            ""
        }
        val cascadeClause = if (cascade) {
            " CASCADE"
        } else {
            ""
        }

        return "DROP TRIGGER$existenceClause $name ON $table$cascadeClause;"
    }
}
