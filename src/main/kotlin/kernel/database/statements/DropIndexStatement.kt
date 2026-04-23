package kernel.database.migrations.statements

/**
 * Renderiza `DROP INDEX`.
 */
internal data class DropIndexStatement(
    private val name: String,
    private val ifExists: Boolean,
    private val concurrently: Boolean
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
        val existenceClause = if (ifExists) {
            " IF EXISTS"
        } else {
            ""
        }

        return "DROP INDEX$concurrentlyClause$existenceClause $name;"
    }
}
