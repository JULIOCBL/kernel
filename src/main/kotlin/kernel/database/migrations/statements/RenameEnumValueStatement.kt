package kernel.database.migrations.statements

import kernel.database.migrations.support.SqlLiteral

/**
 * Renderiza `ALTER TYPE ... RENAME VALUE`.
 */
internal data class RenameEnumValueStatement(
    private val name: String,
    private val from: String,
    private val to: String
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TYPE $name RENAME VALUE ${SqlLiteral.string(from)} TO ${SqlLiteral.string(to)};"
    }
}
