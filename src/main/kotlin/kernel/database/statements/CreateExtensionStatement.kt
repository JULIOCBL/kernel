package kernel.database.statements

import kernel.database.support.SqlLiteral

/**
 * Renderiza `CREATE EXTENSION`.
 */
internal data class CreateExtensionStatement(
    private val name: String,
    private val ifNotExists: Boolean,
    private val schema: String?,
    private val version: String?
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
        val options = listOfNotNull(
            schema?.let { value -> "SCHEMA $value" },
            version?.let { value -> "VERSION ${SqlLiteral.string(value)}" }
        )
        val optionsClause = options.takeIf { values -> values.isNotEmpty() }
            ?.joinToString(" ", prefix = " WITH ")
            .orEmpty()

        return "CREATE EXTENSION$existenceClause $name$optionsClause;"
    }
}
