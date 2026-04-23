package kernel.database.migrations.statements

/**
 * Renderiza `CREATE VIEW` o `CREATE OR REPLACE VIEW`.
 */
internal data class CreateViewStatement(
    private val name: String,
    private val query: String,
    private val orReplace: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val replaceClause = if (orReplace) {
            " OR REPLACE"
        } else {
            ""
        }

        return "CREATE$replaceClause VIEW $name AS\n$query;"
    }
}
