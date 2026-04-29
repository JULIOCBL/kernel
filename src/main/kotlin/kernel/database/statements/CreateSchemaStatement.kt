package kernel.database.statements

/**
 * Renderiza `CREATE SCHEMA`.
 */
internal data class CreateSchemaStatement(
    private val name: String,
    private val ifNotExists: Boolean
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

        return "CREATE SCHEMA$existenceClause $name;"
    }
}
