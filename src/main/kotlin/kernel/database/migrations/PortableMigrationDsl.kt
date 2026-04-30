package kernel.database.migrations

import kernel.database.collector.MigrationCollector
import kernel.database.schema.ColumnBlueprint
import kernel.database.schema.ColumnDefinition
import kernel.database.schema.TableAlterationBlueprint
import kernel.database.schema.TableBlueprint
import kernel.database.statements.AddColumnStatement
import kernel.database.statements.AlterColumnTypeStatement
import kernel.database.statements.CreateIndexStatement
import kernel.database.statements.CreateTableStatement
import kernel.database.statements.CreateViewStatement
import kernel.database.statements.DropColumnDefaultStatement
import kernel.database.statements.DropColumnNotNullStatement
import kernel.database.statements.DropColumnStatement
import kernel.database.statements.DropConstraintStatement
import kernel.database.statements.DropIndexStatement
import kernel.database.statements.DropTableStatement
import kernel.database.statements.DropViewStatement
import kernel.database.statements.RawSqlStatement
import kernel.database.statements.RenameColumnStatement
import kernel.database.statements.RenameConstraintStatement
import kernel.database.statements.RenameTableStatement
import kernel.database.statements.SetColumnDefaultStatement
import kernel.database.statements.SetColumnNotNullStatement
import kernel.database.support.SqlIdentifier

/**
 * Implementacion del subconjunto portable del DSL de migraciones.
 */
internal class PortableMigrationDsl(
    private val collectorProvider: () -> MigrationCollector
) {
    fun createTable(
        name: String,
        ifNotExists: Boolean,
        definition: TableBlueprint.() -> Unit
    ) {
        val table = TableBlueprint(name).apply(definition).build()
        collector().add(CreateTableStatement(table, ifNotExists))
    }

    fun table(name: String, definition: TableAlterationBlueprint.() -> Unit) {
        TableAlterationBlueprint(
            table = MigrationDslSupport.tableName(name),
            collector = collector()
        ).apply(definition)
    }

    fun dropTable(name: String, ifExists: Boolean) {
        collector().add(
            DropTableStatement(
                name = MigrationDslSupport.tableName(name),
                ifExists = ifExists
            )
        )
    }

    fun addColumn(
        table: String,
        ifNotExists: Boolean,
        definition: ColumnBlueprint.() -> ColumnDefinition
    ) {
        collector().add(
            AddColumnStatement(
                table = MigrationDslSupport.tableName(table),
                column = ColumnBlueprint().build(definition),
                ifNotExists = ifNotExists
            )
        )
    }

    fun dropColumn(
        table: String,
        column: String,
        ifExists: Boolean,
        cascade: Boolean
    ) {
        collector().add(
            DropColumnStatement(
                table = MigrationDslSupport.tableName(table),
                column = MigrationDslSupport.columnName(column),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun renameColumn(table: String, from: String, to: String) {
        collector().add(
            RenameColumnStatement(
                table = MigrationDslSupport.tableName(table),
                from = MigrationDslSupport.columnName(from),
                to = MigrationDslSupport.columnName(to)
            )
        )
    }

    fun renameTable(from: String, to: String) {
        collector().add(
            RenameTableStatement(
                from = MigrationDslSupport.tableName(from),
                to = MigrationDslSupport.tableName(to)
            )
        )
    }

    fun alterColumnType(
        table: String,
        column: String,
        type: String,
        usingExpression: String?
    ) {
        collector().add(
            AlterColumnTypeStatement(
                table = MigrationDslSupport.tableName(table),
                column = MigrationDslSupport.columnName(column),
                type = MigrationDslSupport.sqlFragment(type, "Tipo de columna"),
                usingExpression = usingExpression?.let { expression ->
                    MigrationDslSupport.sqlFragment(expression, "Expresion USING")
                }
            )
        )
    }

    fun setColumnDefault(table: String, column: String, expression: String) {
        collector().add(
            SetColumnDefaultStatement(
                table = MigrationDslSupport.tableName(table),
                column = MigrationDslSupport.columnName(column),
                expression = MigrationDslSupport.sqlFragment(expression, "Expresion default")
            )
        )
    }

    fun dropColumnDefault(table: String, column: String) {
        collector().add(
            DropColumnDefaultStatement(
                table = MigrationDslSupport.tableName(table),
                column = MigrationDslSupport.columnName(column)
            )
        )
    }

    fun setColumnNotNull(table: String, column: String) {
        collector().add(
            SetColumnNotNullStatement(
                table = MigrationDslSupport.tableName(table),
                column = MigrationDslSupport.columnName(column)
            )
        )
    }

    fun dropColumnNotNull(table: String, column: String) {
        collector().add(
            DropColumnNotNullStatement(
                table = MigrationDslSupport.tableName(table),
                column = MigrationDslSupport.columnName(column)
            )
        )
    }

    fun dropConstraint(
        table: String,
        name: String,
        ifExists: Boolean,
        cascade: Boolean
    ) {
        collector().add(
            DropConstraintStatement(
                table = MigrationDslSupport.tableName(table),
                name = MigrationDslSupport.constraintName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun renameConstraint(table: String, from: String, to: String) {
        collector().add(
            RenameConstraintStatement(
                table = MigrationDslSupport.tableName(table),
                from = MigrationDslSupport.constraintName(from),
                to = MigrationDslSupport.constraintName(to)
            )
        )
    }

    fun createIndex(
        name: String,
        table: String,
        columns: List<String>,
        unique: Boolean,
        ifNotExists: Boolean,
        concurrently: Boolean,
        using: String?,
        include: List<String>,
        where: String?
    ) {
        collector().add(
            CreateIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                table = MigrationDslSupport.tableName(table),
                columns = MigrationDslSupport.columnNames(columns),
                unique = unique,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = using?.let { method ->
                    SqlIdentifier.requireValid(method, "Metodo de indice")
                },
                include = MigrationDslSupport.columnNamesOrEmpty(include),
                where = where?.let { expression ->
                    MigrationDslSupport.sqlFragment(expression, "Expresion WHERE")
                }
            )
        )
    }

    fun dropIndex(
        name: String,
        ifExists: Boolean,
        concurrently: Boolean,
        table: String?
    ) {
        collector().add(
            DropIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                ifExists = ifExists,
                concurrently = concurrently,
                table = table?.let(MigrationDslSupport::tableName)
            )
        )
    }

    fun createView(name: String, query: String, orReplace: Boolean) {
        collector().add(
            CreateViewStatement(
                name = MigrationDslSupport.relationName(name),
                query = MigrationDslSupport.sqlFragment(query, "Query de vista"),
                orReplace = orReplace
            )
        )
    }

    fun dropView(name: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropViewStatement(
                name = MigrationDslSupport.relationName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun statement(sql: String) {
        collector().add(RawSqlStatement(sql))
    }

    private fun collector(): MigrationCollector {
        return collectorProvider()
    }
}
