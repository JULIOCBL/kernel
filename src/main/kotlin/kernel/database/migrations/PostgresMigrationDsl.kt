package kernel.database.migrations

import kernel.database.collector.MigrationCollector
import kernel.database.statements.AddEnumValueStatement
import kernel.database.statements.CommentStatement
import kernel.database.statements.CreateDomainStatement
import kernel.database.statements.CreateEnumStatement
import kernel.database.statements.CreateExtensionStatement
import kernel.database.statements.CreateFunctionStatement
import kernel.database.statements.CreateMaterializedViewStatement
import kernel.database.statements.CreateSchemaStatement
import kernel.database.statements.CreateSequenceStatement
import kernel.database.statements.CreateTriggerStatement
import kernel.database.statements.DropDomainStatement
import kernel.database.statements.DropEnumStatement
import kernel.database.statements.DropExtensionStatement
import kernel.database.statements.DropFunctionStatement
import kernel.database.statements.DropMaterializedViewStatement
import kernel.database.statements.DropSchemaStatement
import kernel.database.statements.DropSequenceStatement
import kernel.database.statements.DropTriggerStatement
import kernel.database.statements.RefreshMaterializedViewStatement
import kernel.database.statements.RenameEnumStatement
import kernel.database.statements.RenameEnumValueStatement
import kernel.database.statements.RenameSchemaStatement
import kernel.database.statements.RenameSequenceStatement
import kernel.database.support.SqlIdentifier

/**
 * Implementacion de primitivas orientadas a PostgreSQL dentro del DSL.
 */
internal class PostgresMigrationDsl(
    private val collectorProvider: () -> MigrationCollector
) {
    fun createSchema(name: String, ifNotExists: Boolean) {
        collector().add(
            CreateSchemaStatement(
                name = MigrationDslSupport.schemaName(name),
                ifNotExists = ifNotExists
            )
        )
    }

    fun dropSchema(name: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropSchemaStatement(
                name = MigrationDslSupport.schemaName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun renameSchema(from: String, to: String) {
        collector().add(
            RenameSchemaStatement(
                from = MigrationDslSupport.schemaName(from),
                to = MigrationDslSupport.schemaName(to)
            )
        )
    }

    fun createExtension(
        name: String,
        ifNotExists: Boolean,
        schema: String?,
        version: String?
    ) {
        collector().add(
            CreateExtensionStatement(
                name = MigrationDslSupport.extensionName(name),
                ifNotExists = ifNotExists,
                schema = schema?.let(MigrationDslSupport::schemaName),
                version = version?.let { value ->
                    MigrationDslSupport.sqlFragment(value, "Version de extension")
                }
            )
        )
    }

    fun dropExtension(name: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropExtensionStatement(
                name = MigrationDslSupport.extensionName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun createEnum(name: String, values: List<String>) {
        collector().add(
            CreateEnumStatement(
                name = MigrationDslSupport.typeName(name),
                values = MigrationDslSupport.enumValues(values)
            )
        )
    }

    fun dropEnum(name: String, ifExists: Boolean) {
        collector().add(
            DropEnumStatement(
                name = MigrationDslSupport.typeName(name),
                ifExists = ifExists
            )
        )
    }

    fun addEnumValue(
        name: String,
        value: String,
        ifNotExists: Boolean,
        before: String?,
        after: String?
    ) {
        require(before == null || after == null) {
            "Solo puedes usar before o after, no ambos."
        }

        collector().add(
            AddEnumValueStatement(
                name = MigrationDslSupport.typeName(name),
                value = MigrationDslSupport.enumValue(value),
                ifNotExists = ifNotExists,
                before = before?.let(MigrationDslSupport::enumValue),
                after = after?.let(MigrationDslSupport::enumValue)
            )
        )
    }

    fun renameEnumValue(name: String, from: String, to: String) {
        collector().add(
            RenameEnumValueStatement(
                name = MigrationDslSupport.typeName(name),
                from = MigrationDslSupport.enumValue(from),
                to = MigrationDslSupport.enumValue(to)
            )
        )
    }

    fun renameEnum(from: String, to: String) {
        collector().add(
            RenameEnumStatement(
                from = MigrationDslSupport.typeName(from),
                to = MigrationDslSupport.typeName(to)
            )
        )
    }

    fun createDomain(
        name: String,
        type: String,
        notNull: Boolean,
        defaultExpression: String?,
        checkExpression: String?
    ) {
        collector().add(
            CreateDomainStatement(
                name = MigrationDslSupport.typeName(name),
                type = MigrationDslSupport.sqlFragment(type, "Tipo de dominio"),
                notNull = notNull,
                defaultExpression = defaultExpression?.let { expression ->
                    MigrationDslSupport.sqlFragment(expression, "Expresion default")
                },
                checkExpression = checkExpression?.let { expression ->
                    MigrationDslSupport.sqlFragment(expression, "Expresion CHECK")
                }
            )
        )
    }

    fun dropDomain(name: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropDomainStatement(
                name = MigrationDslSupport.typeName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun createMaterializedView(
        name: String,
        query: String,
        ifNotExists: Boolean,
        withData: Boolean
    ) {
        collector().add(
            CreateMaterializedViewStatement(
                name = MigrationDslSupport.relationName(name),
                query = MigrationDslSupport.sqlFragment(query, "Query de vista materializada"),
                ifNotExists = ifNotExists,
                withData = withData
            )
        )
    }

    fun dropMaterializedView(name: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropMaterializedViewStatement(
                name = MigrationDslSupport.relationName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun refreshMaterializedView(name: String, concurrently: Boolean, withData: Boolean) {
        collector().add(
            RefreshMaterializedViewStatement(
                name = MigrationDslSupport.relationName(name),
                concurrently = concurrently,
                withData = withData
            )
        )
    }

    fun createSequence(
        name: String,
        ifNotExists: Boolean,
        incrementBy: Long?,
        minValue: Long?,
        maxValue: Long?,
        startWith: Long?,
        cache: Long?,
        cycle: Boolean,
        ownedBy: String?
    ) {
        collector().add(
            CreateSequenceStatement(
                name = MigrationDslSupport.relationName(name),
                ifNotExists = ifNotExists,
                incrementBy = incrementBy,
                minValue = minValue,
                maxValue = maxValue,
                startWith = startWith,
                cache = cache,
                cycle = cycle,
                ownedBy = ownedBy?.let { value ->
                    MigrationDslSupport.sqlFragment(value, "OWNED BY")
                }
            )
        )
    }

    fun dropSequence(name: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropSequenceStatement(
                name = MigrationDslSupport.relationName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun renameSequence(from: String, to: String) {
        collector().add(
            RenameSequenceStatement(
                from = MigrationDslSupport.relationName(from),
                to = MigrationDslSupport.relationName(to)
            )
        )
    }

    fun commentOnTable(table: String, comment: String?) {
        collector().add(
            CommentStatement(
                target = "TABLE ${MigrationDslSupport.relationName(table)}",
                comment = comment
            )
        )
    }

    fun commentOnColumn(table: String, column: String, comment: String?) {
        collector().add(
            CommentStatement(
                target = "COLUMN ${MigrationDslSupport.relationName(table)}.${MigrationDslSupport.columnName(column)}",
                comment = comment
            )
        )
    }

    fun createFunction(
        name: String,
        returns: String,
        language: String,
        body: String,
        arguments: String,
        orReplace: Boolean
    ) {
        collector().add(
            CreateFunctionStatement(
                name = MigrationDslSupport.relationName(name),
                arguments = arguments.trim(),
                returns = MigrationDslSupport.sqlFragment(returns, "Tipo de retorno"),
                language = SqlIdentifier.requireValid(language, "Lenguaje de funcion"),
                body = MigrationDslSupport.sqlFragment(body, "Cuerpo de funcion"),
                orReplace = orReplace
            )
        )
    }

    fun dropFunction(signature: String, ifExists: Boolean, cascade: Boolean) {
        collector().add(
            DropFunctionStatement(
                signature = MigrationDslSupport.sqlFragment(signature, "Firma de funcion"),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    fun createTrigger(
        name: String,
        table: String,
        timing: String,
        events: List<String>,
        function: String,
        forEach: String,
        whenExpression: String?
    ) {
        collector().add(
            CreateTriggerStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de trigger"),
                table = MigrationDslSupport.relationName(table),
                timing = MigrationDslSupport.triggerTiming(timing),
                events = MigrationDslSupport.triggerEvents(events),
                function = MigrationDslSupport.functionSignature(function),
                forEach = MigrationDslSupport.triggerForEach(forEach),
                whenExpression = whenExpression?.let { expression ->
                    MigrationDslSupport.sqlFragment(expression, "Expresion WHEN")
                }
            )
        )
    }

    fun dropTrigger(
        name: String,
        table: String,
        ifExists: Boolean,
        cascade: Boolean
    ) {
        collector().add(
            DropTriggerStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de trigger"),
                table = MigrationDslSupport.relationName(table),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    private fun collector(): MigrationCollector {
        return collectorProvider()
    }
}
