package kernel.database.schema

import kernel.database.collector.MigrationCollector
import kernel.database.statements.AddTableConstraintStatement
import kernel.database.statements.CreateIndexStatement
import kernel.database.support.SqlIdentifier

/**
 * Helpers PostgreSQL para `ALTER TABLE`.
 */
internal class PostgresTableAlterationDsl(
    private val table: String,
    private val collector: MigrationCollector
) {
    fun gin(
        columns: Array<out String>,
        name: String,
        ifNotExists: Boolean,
        concurrently: Boolean,
        where: String?
    ) {
        indexWithMethod(
            columns = columns,
            name = name,
            method = "gin",
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            where = where
        )
    }

    fun gist(
        columns: Array<out String>,
        name: String,
        ifNotExists: Boolean,
        concurrently: Boolean,
        where: String?
    ) {
        indexWithMethod(
            columns = columns,
            name = name,
            method = "gist",
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            where = where
        )
    }

    fun brin(
        columns: Array<out String>,
        name: String,
        ifNotExists: Boolean,
        concurrently: Boolean,
        where: String?
    ) {
        indexWithMethod(
            columns = columns,
            name = name,
            method = "brin",
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            where = where
        )
    }

    fun indexExpression(
        name: String,
        expression: String,
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
                columns = listOf(TableDslSupport.sqlFragment(expression, "Expresion de indice")),
                unique = unique,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = using?.let { method ->
                    SqlIdentifier.requireValid(method, "Metodo de indice")
                },
                include = TableDslSupport.columnNamesOrEmpty(include),
                where = where?.let { expressionValue ->
                    TableDslSupport.sqlFragment(expressionValue, "Expresion WHERE")
                }
            )
        )
    }

    fun exclude(
        name: String,
        using: String,
        elements: Array<out String>,
        where: String?
    ) {
        require(elements.isNotEmpty()) {
            "Debes indicar al menos un elemento para EXCLUDE."
        }

        collector.add(
            AddTableConstraintStatement(
                table = table,
                constraint = ExcludeConstraintDefinition(
                    name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                    using = SqlIdentifier.requireValid(using, "Metodo de indice"),
                    elements = elements.map { element ->
                        TableDslSupport.sqlFragment(element, "Elemento EXCLUDE")
                    },
                    where = where?.let { expression ->
                        TableDslSupport.sqlFragment(expression, "Expresion WHERE")
                    }
                )
            )
        )
    }

    private fun indexWithMethod(
        columns: Array<out String>,
        name: String,
        method: String,
        ifNotExists: Boolean,
        concurrently: Boolean,
        where: String?
    ) {
        collector.add(
            CreateIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                table = table,
                columns = TableDslSupport.columnNames(columns.toList()),
                unique = false,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = method,
                include = emptyList(),
                where = where?.let { expression ->
                    TableDslSupport.sqlFragment(expression, "Expresion WHERE")
                }
            )
        )
    }
}
