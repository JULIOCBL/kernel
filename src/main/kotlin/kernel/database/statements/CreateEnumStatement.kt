package kernel.database.migrations.statements

import kernel.database.migrations.support.SqlLiteral

/**
 * Renderiza `CREATE TYPE ... AS ENUM`.
 */
internal data class CreateEnumStatement(
    private val name: String,
    private val values: List<String>
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val enumValues = values.joinToString(", ") { value -> SqlLiteral.string(value) }

        return "CREATE TYPE $name AS ENUM ($enumValues);"
    }
}
