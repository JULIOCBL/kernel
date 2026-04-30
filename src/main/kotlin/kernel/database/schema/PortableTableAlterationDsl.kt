package kernel.database.schema

import kernel.database.collector.MigrationCollector
import kernel.database.statements.AddColumnStatement
import kernel.database.statements.AddTableConstraintStatement
import kernel.database.statements.AlterColumnTypeStatement
import kernel.database.statements.CreateIndexStatement
import kernel.database.statements.DropColumnDefaultStatement
import kernel.database.statements.DropColumnNotNullStatement
import kernel.database.statements.DropColumnStatement
import kernel.database.statements.DropConstraintStatement
import kernel.database.statements.DropIndexStatement
import kernel.database.statements.RenameColumnStatement
import kernel.database.statements.RenameConstraintStatement
import kernel.database.statements.SetColumnDefaultStatement
import kernel.database.statements.SetColumnNotNullStatement
import kernel.database.support.SqlIdentifier

/**
 * Helpers portables para `ALTER TABLE`.
 */
internal class PortableTableAlterationDsl(
    private val table: String,
    private val collector: MigrationCollector
) {
    fun addColumn(name: String, type: String): ColumnDefinition {
        val columnName = TableDslSupport.columnName(name)
        val column = ColumnDefinition(columnName, type)

        collector.add(
            AddColumnStatement(
                table = table,
                column = column,
                ifNotExists = true
            )
        )

        return column
    }

    fun dropColumn(
        columns: Array<out String>,
        ifExists: Boolean,
        cascade: Boolean
    ) {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna."
        }

        for (column in columns) {
            collector.add(
                DropColumnStatement(
                    table = table,
                    column = TableDslSupport.columnName(column),
                    ifExists = ifExists,
                    cascade = cascade
                )
            )
        }
    }

    fun renameColumn(from: String, to: String) {
        collector.add(
            RenameColumnStatement(
                table = table,
                from = TableDslSupport.columnName(from),
                to = TableDslSupport.columnName(to)
            )
        )
    }

    fun changeColumnType(
        column: String,
        type: String,
        usingExpression: String?
    ) {
        collector.add(
            AlterColumnTypeStatement(
                table = table,
                column = TableDslSupport.columnName(column),
                type = TableDslSupport.sqlFragment(type, "Tipo de columna"),
                usingExpression = usingExpression?.let { expression ->
                    TableDslSupport.sqlFragment(expression, "Expresion USING")
                }
            )
        )
    }

    fun setDefault(column: String, expression: String) {
        collector.add(
            SetColumnDefaultStatement(
                table = table,
                column = TableDslSupport.columnName(column),
                expression = TableDslSupport.sqlFragment(expression, "Expresion default")
            )
        )
    }

    fun dropDefault(column: String) {
        collector.add(
            DropColumnDefaultStatement(
                table = table,
                column = TableDslSupport.columnName(column)
            )
        )
    }

    fun setNotNull(column: String) {
        collector.add(
            SetColumnNotNullStatement(
                table = table,
                column = TableDslSupport.columnName(column)
            )
        )
    }

    fun dropNotNull(column: String) {
        collector.add(
            DropColumnNotNullStatement(
                table = table,
                column = TableDslSupport.columnName(column)
            )
        )
    }

    fun index(
        columns: Array<out String>,
        name: String,
        unique: Boolean,
        ifNotExists: Boolean,
        concurrently: Boolean,
        using: String?,
        include: List<String>,
        where: String?
    ) {
        addIndex(
            name = name,
            columns = columns.toList(),
            unique = unique,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    fun dropIndex(name: String, ifExists: Boolean, concurrently: Boolean) {
        collector.add(
            DropIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                ifExists = ifExists,
                concurrently = concurrently,
                table = table
            )
        )
    }

    fun foreign(vararg columns: String, name: String?): ForeignKeyDefinition {
        val normalizedColumns = TableDslSupport.columnNames(columns.toList())
        val foreignKey = ForeignKeyDefinition(
            name = name?.let { value ->
                SqlIdentifier.requireValid(value, "Nombre de foreign key")
            } ?: defaultIndexName(normalizedColumns, "foreign"),
            columns = normalizedColumns
        )

        collector.add(AddTableConstraintStatement(table, foreignKey))

        return foreignKey
    }

    fun check(name: String, expression: String) {
        collector.add(
            AddTableConstraintStatement(
                table = table,
                constraint = CheckConstraintDefinition(
                    name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                    expression = TableDslSupport.sqlFragment(expression, "Expresion CHECK")
                )
            )
        )
    }

    fun uniqueConstraint(vararg columns: String, name: String?) {
        val normalizedColumns = TableDslSupport.columnNames(columns.toList())

        collector.add(
            AddTableConstraintStatement(
                table = table,
                constraint = UniqueConstraintDefinition(
                    name = name?.let { value ->
                        SqlIdentifier.requireValid(value, "Nombre de constraint")
                    } ?: defaultIndexName(normalizedColumns, "unique"),
                    columns = normalizedColumns
                )
            )
        )
    }

    fun dropConstraint(name: String, ifExists: Boolean, cascade: Boolean) {
        collector.add(
            DropConstraintStatement(
                table = table,
                name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun renameConstraint(from: String, to: String) {
        collector.add(
            RenameConstraintStatement(
                table = table,
                from = SqlIdentifier.requireValid(from, "Nombre de constraint"),
                to = SqlIdentifier.requireValid(to, "Nombre de constraint")
            )
        )
    }

    internal fun defaultIndexName(columns: List<String>, suffix: String): String {
        return TableDslSupport.defaultName(table, TableDslSupport.columnNames(columns), suffix)
    }

    internal fun addIndex(
        name: String,
        columns: List<String>,
        unique: Boolean,
        ifNotExists: Boolean,
        concurrently: Boolean,
        using: String?,
        include: List<String>,
        where: String?
    ) {
        collector.add(
            CreateIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                table = table,
                columns = TableDslSupport.columnNames(columns),
                unique = unique,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = using?.let { method ->
                    SqlIdentifier.requireValid(method, "Metodo de indice")
                },
                include = TableDslSupport.columnNamesOrEmpty(include),
                where = where?.let { expression ->
                    TableDslSupport.sqlFragment(expression, "Expresion WHERE")
                }
            )
        )
    }
}
