package kernel.database.migrations.statements

/**
 * Renderiza `DROP TYPE` para ENUM y otros tipos PostgreSQL.
 */
internal data class DropEnumStatement(
    private val name: String,
    private val ifExists: Boolean
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

        return "DROP TYPE$existenceClause $name;"
    }
}
