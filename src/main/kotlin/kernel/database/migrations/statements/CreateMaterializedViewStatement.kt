package kernel.database.migrations.statements

/**
 * Renderiza `CREATE MATERIALIZED VIEW`.
 */
internal data class CreateMaterializedViewStatement(
    private val name: String,
    private val query: String,
    private val ifNotExists: Boolean,
    private val withData: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val existenceClause = if (ifNotExists) {
            " IF NOT EXISTS"
        } else {
            ""
        }
        val dataClause = if (withData) {
            " WITH DATA"
        } else {
            " WITH NO DATA"
        }

        return "CREATE MATERIALIZED VIEW$existenceClause $name AS\n$query$dataClause;"
    }
}
