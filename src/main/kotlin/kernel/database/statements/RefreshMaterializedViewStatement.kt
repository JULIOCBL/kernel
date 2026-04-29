package kernel.database.statements

/**
 * Renderiza `REFRESH MATERIALIZED VIEW`.
 */
internal data class RefreshMaterializedViewStatement(
    private val name: String,
    private val concurrently: Boolean,
    private val withData: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val concurrentlyClause = if (concurrently) {
            " CONCURRENTLY"
        } else {
            ""
        }
        val dataClause = if (withData) {
            " WITH DATA"
        } else {
            " WITH NO DATA"
        }

        return "REFRESH MATERIALIZED VIEW$concurrentlyClause $name$dataClause;"
    }
}
