package kernel.database.migrations.statements

import kernel.database.migrations.schema.TableDefinition

/**
 * Renderiza `CREATE TABLE` desde una definicion validada.
 */
internal data class CreateTableStatement(
    private val table: TableDefinition,
    private val ifNotExists: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val prefix = if (ifNotExists) {
            "CREATE TABLE IF NOT EXISTS"
        } else {
            "CREATE TABLE"
        }
        val lines = table.columns.map { column -> column.toSql() } +
            listOfNotNull(table.primaryKey?.toSql()) +
            table.constraints.map { constraint -> constraint.toSql() }
        val body = lines.joinToString(",\n") { line -> "    $line" }

        return "$prefix ${table.name} (\n$body\n);"
    }
}
