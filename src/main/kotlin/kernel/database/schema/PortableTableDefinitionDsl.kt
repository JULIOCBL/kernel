package kernel.database.schema

import kernel.database.support.SqlIdentifier

/**
 * Helpers portables para constraints declaradas al crear tablas.
 */
internal class PortableTableDefinitionDsl(
    private val tableName: String,
    private val addConstraint: (TableConstraintDefinition) -> Unit,
    private val setPrimaryKey: (PrimaryKeyDefinition) -> Unit
) {
    fun primaryKey(vararg columns: String) {
        setPrimaryKey(PrimaryKeyDefinition(TableDslSupport.primaryKeyColumns(columns)))
    }

    fun unique(vararg columns: String, name: String?) {
        val normalizedColumns = TableDslSupport.columnNames(columns.toList())

        addConstraint(
            UniqueConstraintDefinition(
                name = name?.let { value ->
                    SqlIdentifier.requireValid(value, "Nombre de constraint")
                } ?: TableDslSupport.defaultName(tableName, normalizedColumns, "unique"),
                columns = normalizedColumns
            )
        )
    }

    fun check(name: String, expression: String) {
        addConstraint(
            CheckConstraintDefinition(
                name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                expression = TableDslSupport.sqlFragment(expression, "Expresion CHECK")
            )
        )
    }

    fun foreign(vararg columns: String, name: String?): ForeignKeyDefinition {
        val normalizedColumns = TableDslSupport.columnNames(columns.toList())
        val foreignKey = ForeignKeyDefinition(
            name = name?.let { value ->
                SqlIdentifier.requireValid(value, "Nombre de foreign key")
            } ?: TableDslSupport.defaultName(tableName, normalizedColumns, "foreign"),
            columns = normalizedColumns
        )

        addConstraint(foreignKey)

        return foreignKey
    }
}
