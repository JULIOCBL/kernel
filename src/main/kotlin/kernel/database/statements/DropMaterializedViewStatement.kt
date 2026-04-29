package kernel.database.statements

/**
 * Renderiza `DROP MATERIALIZED VIEW`.
 */
internal data class DropMaterializedViewStatement(
    private val name: String,
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

        return "DROP MATERIALIZED VIEW$existenceClause $name$cascadeClause;"
    }
}
