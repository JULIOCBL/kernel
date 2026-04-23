package kernel.database.migrations.statements

import kernel.database.migrations.support.SqlLiteral

/**
 * Renderiza `ALTER TYPE ... ADD VALUE` para ENUM PostgreSQL.
 */
internal data class AddEnumValueStatement(
    private val name: String,
    private val value: String,
    private val ifNotExists: Boolean,
    private val before: String?,
    private val after: String?
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
        val positionClause = when {
            before != null -> " BEFORE ${SqlLiteral.string(before)}"
            after != null -> " AFTER ${SqlLiteral.string(after)}"
            else -> ""
        }

        return "ALTER TYPE $name ADD VALUE$existenceClause ${SqlLiteral.string(value)}$positionClause;"
    }
}
