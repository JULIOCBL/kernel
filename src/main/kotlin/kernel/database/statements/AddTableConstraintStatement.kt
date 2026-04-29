package kernel.database.statements

import kernel.database.schema.TableConstraintDefinition

/**
 * Renderiza `ALTER TABLE ... ADD CONSTRAINT`.
 */
internal data class AddTableConstraintStatement(
    private val table: String,
    private val constraint: TableConstraintDefinition
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return "ALTER TABLE $table ADD ${constraint.toSql()};"
    }
}
