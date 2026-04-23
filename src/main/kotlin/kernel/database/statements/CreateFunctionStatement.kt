package kernel.database.migrations.statements

/**
 * Renderiza `CREATE FUNCTION` con cuerpo en dollar quoting.
 */
internal data class CreateFunctionStatement(
    private val name: String,
    private val arguments: String,
    private val returns: String,
    private val language: String,
    private val body: String,
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

        return "CREATE$replaceClause FUNCTION $name($arguments)\nRETURNS $returns\nLANGUAGE $language\nAS \$\$\n$body\n\$\$;"
    }
}
