package kernel.database.migrations

import kernel.database.migrations.collector.MigrationCollector
import kernel.database.migrations.schema.ColumnBlueprint
import kernel.database.migrations.schema.ColumnDefinition
import kernel.database.migrations.schema.TableAlterationBlueprint
import kernel.database.migrations.schema.TableBlueprint
import kernel.database.migrations.statements.AddColumnStatement
import kernel.database.migrations.statements.AddEnumValueStatement
import kernel.database.migrations.statements.AlterColumnTypeStatement
import kernel.database.migrations.statements.CommentStatement
import kernel.database.migrations.statements.CreateDomainStatement
import kernel.database.migrations.statements.CreateEnumStatement
import kernel.database.migrations.statements.CreateExtensionStatement
import kernel.database.migrations.statements.CreateFunctionStatement
import kernel.database.migrations.statements.CreateIndexStatement
import kernel.database.migrations.statements.CreateMaterializedViewStatement
import kernel.database.migrations.statements.CreateSchemaStatement
import kernel.database.migrations.statements.CreateSequenceStatement
import kernel.database.migrations.statements.CreateTableStatement
import kernel.database.migrations.statements.CreateTriggerStatement
import kernel.database.migrations.statements.CreateViewStatement
import kernel.database.migrations.statements.DropColumnDefaultStatement
import kernel.database.migrations.statements.DropColumnNotNullStatement
import kernel.database.migrations.statements.DropColumnStatement
import kernel.database.migrations.statements.DropConstraintStatement
import kernel.database.migrations.statements.DropDomainStatement
import kernel.database.migrations.statements.DropEnumStatement
import kernel.database.migrations.statements.DropExtensionStatement
import kernel.database.migrations.statements.DropFunctionStatement
import kernel.database.migrations.statements.DropIndexStatement
import kernel.database.migrations.statements.DropMaterializedViewStatement
import kernel.database.migrations.statements.DropSchemaStatement
import kernel.database.migrations.statements.DropSequenceStatement
import kernel.database.migrations.statements.DropTableStatement
import kernel.database.migrations.statements.DropTriggerStatement
import kernel.database.migrations.statements.DropViewStatement
import kernel.database.migrations.statements.RawSqlStatement
import kernel.database.migrations.statements.RefreshMaterializedViewStatement
import kernel.database.migrations.statements.RenameColumnStatement
import kernel.database.migrations.statements.RenameConstraintStatement
import kernel.database.migrations.statements.RenameEnumStatement
import kernel.database.migrations.statements.RenameEnumValueStatement
import kernel.database.migrations.statements.RenameSchemaStatement
import kernel.database.migrations.statements.RenameSequenceStatement
import kernel.database.migrations.statements.RenameTableStatement
import kernel.database.migrations.statements.SetColumnDefaultStatement
import kernel.database.migrations.statements.SetColumnNotNullStatement
import kernel.database.migrations.support.SqlIdentifier

/**
 * Clase base para definir migraciones mediante un DSL pequeno.
 *
 * La migracion registra operaciones en `up` y `down`; despues esas operaciones
 * se pueden convertir a SQL usando `upSql`, `downSql` o `MigrationSqlGenerator`.
 */
abstract class Migration {
    private var collector: MigrationCollector? = null

    /**
     * Convierte las operaciones de `up` a sentencias SQL.
     */
    fun upSql(): List<String> = collect {
        up()
    }

    /**
     * Convierte las operaciones de `down` a sentencias SQL.
     */
    fun downSql(): List<String> = collect {
        down()
    }

    /**
     * Define las operaciones que aplican la migracion.
     */
    abstract fun up()

    /**
     * Define las operaciones que revierten la migracion.
     */
    abstract fun down()

    /**
     * Alias estilo Laravel para `createTable`.
     */
    protected fun create(
        name: String,
        ifNotExists: Boolean = true,
        definition: TableBlueprint.() -> Unit
    ) {
        createTable(name, ifNotExists, definition)
    }

    /**
     * Registra una sentencia `CREATE TABLE`.
     */
    protected fun createTable(
        name: String,
        ifNotExists: Boolean = true,
        definition: TableBlueprint.() -> Unit
    ) {
        val currentCollector = activeCollector()
        val table = TableBlueprint(name).apply(definition).build()

        currentCollector.add(CreateTableStatement(table, ifNotExists))
    }

    /**
     * Alias estilo Laravel para modificar una tabla existente.
     */
    protected fun table(name: String, definition: TableAlterationBlueprint.() -> Unit) {
        TableAlterationBlueprint(
            table = tableName(name),
            collector = activeCollector()
        ).apply(definition)
    }

    /**
     * Alias estilo Laravel para `dropTable(name, ifExists = false)`.
     */
    protected fun drop(name: String) {
        dropTable(name, ifExists = false)
    }

    /**
     * Alias estilo Laravel para `dropTable(name, ifExists = true)`.
     */
    protected fun dropIfExists(name: String) {
        dropTable(name, ifExists = true)
    }

    /**
     * Registra una sentencia `DROP TABLE`.
     */
    protected fun dropTable(name: String, ifExists: Boolean = true) {
        activeCollector().add(
            DropTableStatement(
                name = tableName(name),
                ifExists = ifExists
            )
        )
    }

    /**
     * Registra una sentencia `CREATE SCHEMA` para crear un schema PostgreSQL.
     */
    protected fun createSchema(name: String, ifNotExists: Boolean = true) {
        activeCollector().add(
            CreateSchemaStatement(
                name = schemaName(name),
                ifNotExists = ifNotExists
            )
        )
    }

    /**
     * Registra una sentencia `DROP SCHEMA`, opcionalmente con `CASCADE`.
     */
    protected fun dropSchema(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropSchemaStatement(
                name = schemaName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `ALTER SCHEMA ... RENAME TO`.
     */
    protected fun renameSchema(from: String, to: String) {
        activeCollector().add(
            RenameSchemaStatement(
                from = schemaName(from),
                to = schemaName(to)
            )
        )
    }

    /**
     * Registra una sentencia `CREATE EXTENSION` con schema y version opcionales.
     */
    protected fun createExtension(
        name: String,
        ifNotExists: Boolean = true,
        schema: String? = null,
        version: String? = null
    ) {
        activeCollector().add(
            CreateExtensionStatement(
                name = extensionName(name),
                ifNotExists = ifNotExists,
                schema = schema?.let(::schemaName),
                version = version?.let { value -> sqlFragment(value, "Version de extension") }
            )
        )
    }

    /**
     * Registra una sentencia `DROP EXTENSION`, opcionalmente con `CASCADE`.
     */
    protected fun dropExtension(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropExtensionStatement(
                name = extensionName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Crea un tipo ENUM nativo de PostgreSQL.
     */
    protected fun createEnum(name: String, vararg values: String) {
        activeCollector().add(
            CreateEnumStatement(
                name = typeName(name),
                values = enumValues(values.toList())
            )
        )
    }

    /**
     * Elimina un tipo ENUM nativo de PostgreSQL.
     */
    protected fun dropEnum(name: String, ifExists: Boolean = true) {
        activeCollector().add(
            DropEnumStatement(
                name = typeName(name),
                ifExists = ifExists
            )
        )
    }

    /**
     * Agrega un valor a un ENUM nativo de PostgreSQL.
     */
    protected fun addEnumValue(
        name: String,
        value: String,
        ifNotExists: Boolean = true,
        before: String? = null,
        after: String? = null
    ) {
        require(before == null || after == null) {
            "Solo puedes usar before o after, no ambos."
        }

        activeCollector().add(
            AddEnumValueStatement(
                name = typeName(name),
                value = enumValue(value),
                ifNotExists = ifNotExists,
                before = before?.let(::enumValue),
                after = after?.let(::enumValue)
            )
        )
    }

    /**
     * Renombra un valor de un ENUM nativo de PostgreSQL.
     */
    protected fun renameEnumValue(name: String, from: String, to: String) {
        activeCollector().add(
            RenameEnumValueStatement(
                name = typeName(name),
                from = enumValue(from),
                to = enumValue(to)
            )
        )
    }

    /**
     * Renombra un tipo ENUM nativo de PostgreSQL.
     */
    protected fun renameEnum(from: String, to: String) {
        activeCollector().add(
            RenameEnumStatement(
                from = typeName(from),
                to = typeName(to)
            )
        )
    }

    /**
     * Crea un dominio PostgreSQL sobre un tipo base con reglas opcionales.
     */
    protected fun createDomain(
        name: String,
        type: String,
        notNull: Boolean = false,
        defaultExpression: String? = null,
        checkExpression: String? = null
    ) {
        activeCollector().add(
            CreateDomainStatement(
                name = typeName(name),
                type = sqlFragment(type, "Tipo de dominio"),
                notNull = notNull,
                defaultExpression = defaultExpression?.let { expression ->
                    sqlFragment(expression, "Expresion default")
                },
                checkExpression = checkExpression?.let { expression ->
                    sqlFragment(expression, "Expresion CHECK")
                }
            )
        )
    }

    /**
     * Elimina un dominio PostgreSQL.
     */
    protected fun dropDomain(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropDomainStatement(
                name = typeName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... ADD COLUMN`.
     */
    protected fun addColumn(
        table: String,
        ifNotExists: Boolean = true,
        definition: ColumnBlueprint.() -> ColumnDefinition
    ) {
        activeCollector().add(
            AddColumnStatement(
                table = tableName(table),
                column = ColumnBlueprint().build(definition),
                ifNotExists = ifNotExists
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... DROP COLUMN`.
     */
    protected fun dropColumn(
        table: String,
        column: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropColumnStatement(
                table = tableName(table),
                column = columnName(column),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... RENAME COLUMN`.
     */
    protected fun renameColumn(table: String, from: String, to: String) {
        activeCollector().add(
            RenameColumnStatement(
                table = tableName(table),
                from = columnName(from),
                to = columnName(to)
            )
        )
    }

    /**
     * Alias estilo Laravel para renombrar una tabla.
     */
    protected fun rename(from: String, to: String) {
        renameTable(from, to)
    }

    /**
     * Registra una sentencia `ALTER TABLE ... RENAME TO`.
     */
    protected fun renameTable(from: String, to: String) {
        activeCollector().add(
            RenameTableStatement(
                from = tableName(from),
                to = tableName(to)
            )
        )
    }

    /**
     * Cambia el tipo de una columna.
     */
    protected fun alterColumnType(
        table: String,
        column: String,
        type: String,
        usingExpression: String? = null
    ) {
        activeCollector().add(
            AlterColumnTypeStatement(
                table = tableName(table),
                column = columnName(column),
                type = sqlFragment(type, "Tipo de columna"),
                usingExpression = usingExpression?.let { expression ->
                    sqlFragment(expression, "Expresion USING")
                }
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... ALTER COLUMN ... SET DEFAULT`.
     */
    protected fun setColumnDefault(table: String, column: String, expression: String) {
        activeCollector().add(
            SetColumnDefaultStatement(
                table = tableName(table),
                column = columnName(column),
                expression = sqlFragment(expression, "Expresion default")
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... ALTER COLUMN ... DROP DEFAULT`.
     */
    protected fun dropColumnDefault(table: String, column: String) {
        activeCollector().add(
            DropColumnDefaultStatement(
                table = tableName(table),
                column = columnName(column)
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... ALTER COLUMN ... SET NOT NULL`.
     */
    protected fun setColumnNotNull(table: String, column: String) {
        activeCollector().add(
            SetColumnNotNullStatement(
                table = tableName(table),
                column = columnName(column)
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... ALTER COLUMN ... DROP NOT NULL`.
     */
    protected fun dropColumnNotNull(table: String, column: String) {
        activeCollector().add(
            DropColumnNotNullStatement(
                table = tableName(table),
                column = columnName(column)
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... DROP CONSTRAINT`.
     */
    protected fun dropConstraint(
        table: String,
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropConstraintStatement(
                table = tableName(table),
                name = constraintName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `ALTER TABLE ... RENAME CONSTRAINT`.
     */
    protected fun renameConstraint(table: String, from: String, to: String) {
        activeCollector().add(
            RenameConstraintStatement(
                table = tableName(table),
                from = constraintName(from),
                to = constraintName(to)
            )
        )
    }

    /**
     * Registra una sentencia `CREATE INDEX`.
     */
    protected fun createIndex(
        name: String,
        table: String,
        vararg columns: String,
        unique: Boolean = false,
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        activeCollector().add(
            CreateIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                table = tableName(table),
                columns = columnNames(columns.toList()),
                unique = unique,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = using?.let { method -> SqlIdentifier.requireValid(method, "Metodo de indice") },
                include = columnNamesOrEmpty(include),
                where = where?.let { expression -> sqlFragment(expression, "Expresion WHERE") }
            )
        )
    }

    /**
     * Registra una sentencia `DROP INDEX`.
     */
    protected fun dropIndex(
        name: String,
        ifExists: Boolean = true,
        concurrently: Boolean = false
    ) {
        activeCollector().add(
            DropIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                ifExists = ifExists,
                concurrently = concurrently
            )
        )
    }

    /**
     * Registra una sentencia `CREATE VIEW` o `CREATE OR REPLACE VIEW`.
     */
    protected fun createView(
        name: String,
        query: String,
        orReplace: Boolean = true
    ) {
        activeCollector().add(
            CreateViewStatement(
                name = relationName(name),
                query = sqlFragment(query, "Query de vista"),
                orReplace = orReplace
            )
        )
    }

    /**
     * Registra una sentencia `DROP VIEW`.
     */
    protected fun dropView(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropViewStatement(
                name = relationName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `CREATE MATERIALIZED VIEW`.
     */
    protected fun createMaterializedView(
        name: String,
        query: String,
        ifNotExists: Boolean = true,
        withData: Boolean = true
    ) {
        activeCollector().add(
            CreateMaterializedViewStatement(
                name = relationName(name),
                query = sqlFragment(query, "Query de vista materializada"),
                ifNotExists = ifNotExists,
                withData = withData
            )
        )
    }

    /**
     * Registra una sentencia `DROP MATERIALIZED VIEW`.
     */
    protected fun dropMaterializedView(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropMaterializedViewStatement(
                name = relationName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `REFRESH MATERIALIZED VIEW`.
     */
    protected fun refreshMaterializedView(
        name: String,
        concurrently: Boolean = false,
        withData: Boolean = true
    ) {
        activeCollector().add(
            RefreshMaterializedViewStatement(
                name = relationName(name),
                concurrently = concurrently,
                withData = withData
            )
        )
    }

    /**
     * Registra una sentencia `CREATE SEQUENCE` con opciones comunes de PostgreSQL.
     */
    protected fun createSequence(
        name: String,
        ifNotExists: Boolean = true,
        incrementBy: Long? = null,
        minValue: Long? = null,
        maxValue: Long? = null,
        startWith: Long? = null,
        cache: Long? = null,
        cycle: Boolean = false,
        ownedBy: String? = null
    ) {
        activeCollector().add(
            CreateSequenceStatement(
                name = relationName(name),
                ifNotExists = ifNotExists,
                incrementBy = incrementBy,
                minValue = minValue,
                maxValue = maxValue,
                startWith = startWith,
                cache = cache,
                cycle = cycle,
                ownedBy = ownedBy?.let { value -> sqlFragment(value, "OWNED BY") }
            )
        )
    }

    /**
     * Registra una sentencia `DROP SEQUENCE`.
     */
    protected fun dropSequence(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropSequenceStatement(
                name = relationName(name),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `ALTER SEQUENCE ... RENAME TO`.
     */
    protected fun renameSequence(from: String, to: String) {
        activeCollector().add(
            RenameSequenceStatement(
                from = relationName(from),
                to = relationName(to)
            )
        )
    }

    /**
     * Registra un comentario para una tabla o lo elimina cuando `comment` es null.
     */
    protected fun commentOnTable(table: String, comment: String?) {
        activeCollector().add(
            CommentStatement(
                target = "TABLE ${relationName(table)}",
                comment = comment
            )
        )
    }

    /**
     * Registra un comentario para una columna o lo elimina cuando `comment` es null.
     */
    protected fun commentOnColumn(table: String, column: String, comment: String?) {
        activeCollector().add(
            CommentStatement(
                target = "COLUMN ${relationName(table)}.${columnName(column)}",
                comment = comment
            )
        )
    }

    /**
     * Registra una funcion PostgreSQL usando dollar quoting para el cuerpo.
     */
    protected fun createFunction(
        name: String,
        returns: String,
        language: String,
        body: String,
        arguments: String = "",
        orReplace: Boolean = true
    ) {
        activeCollector().add(
            CreateFunctionStatement(
                name = relationName(name),
                arguments = arguments.trim(),
                returns = sqlFragment(returns, "Tipo de retorno"),
                language = SqlIdentifier.requireValid(language, "Lenguaje de funcion"),
                body = sqlFragment(body, "Cuerpo de funcion"),
                orReplace = orReplace
            )
        )
    }

    /**
     * Registra una sentencia `DROP FUNCTION` usando la firma completa.
     */
    protected fun dropFunction(
        signature: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropFunctionStatement(
                signature = sqlFragment(signature, "Firma de funcion"),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra una sentencia `CREATE TRIGGER` enlazada a una funcion PostgreSQL.
     */
    protected fun createTrigger(
        name: String,
        table: String,
        timing: String,
        vararg events: String,
        function: String,
        forEach: String = "ROW",
        whenExpression: String? = null
    ) {
        activeCollector().add(
            CreateTriggerStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de trigger"),
                table = relationName(table),
                timing = triggerTiming(timing),
                events = triggerEvents(events.toList()),
                function = functionSignature(function),
                forEach = triggerForEach(forEach),
                whenExpression = whenExpression?.let { expression ->
                    sqlFragment(expression, "Expresion WHEN")
                }
            )
        )
    }

    /**
     * Registra una sentencia `DROP TRIGGER`.
     */
    protected fun dropTrigger(
        name: String,
        table: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        activeCollector().add(
            DropTriggerStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de trigger"),
                table = relationName(table),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Registra SQL manual para casos especiales que el DSL aun no cubre.
     */
    protected fun statement(sql: String) {
        activeCollector().add(RawSqlStatement(sql))
    }

    /**
     * Ejecuta un bloque de migracion con un collector aislado y devuelve su SQL.
     */
    private fun collect(block: () -> Unit): List<String> {
        val previousCollector = collector
        val currentCollector = MigrationCollector()

        collector = currentCollector

        try {
            block()
            return currentCollector.toSql()
        } finally {
            collector = previousCollector
        }
    }

    /**
     * Obtiene el collector activo o falla si se invoca el DSL fuera de generacion.
     */
    private fun activeCollector(): MigrationCollector {
        return collector
            ?: error("Las operaciones de migracion solo se pueden usar al generar SQL.")
    }

    /**
     * Valida nombres de tabla, incluyendo nombres calificados por schema.
     */
    private fun tableName(name: String): String {
        return SqlIdentifier.requireQualified(name, "Nombre de tabla")
    }

    /**
     * Valida nombres de relaciones PostgreSQL como tablas, vistas y secuencias.
     */
    private fun relationName(name: String): String {
        return SqlIdentifier.requireQualified(name, "Nombre de relacion")
    }

    /**
     * Valida un nombre simple de schema.
     */
    private fun schemaName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de schema")
    }

    /**
     * Valida un nombre simple de extension.
     */
    private fun extensionName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de extension")
    }

    /**
     * Valida un nombre simple de constraint.
     */
    private fun constraintName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de constraint")
    }

    /**
     * Valida un nombre simple de tipo PostgreSQL.
     */
    private fun typeName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de tipo")
    }

    /**
     * Valida un nombre simple de columna.
     */
    private fun columnName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de columna")
    }

    /**
     * Valida una lista no vacia de columnas sin duplicados.
     */
    private fun columnNames(columns: List<String>): List<String> {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna."
        }

        val normalizedColumns = columns.map { column -> columnName(column) }

        require(normalizedColumns.distinct().size == normalizedColumns.size) {
            "No puedes repetir columnas."
        }

        return normalizedColumns
    }

    /**
     * Valida columnas solo cuando se proporciona una lista no vacia.
     */
    private fun columnNamesOrEmpty(columns: List<String>): List<String> {
        return if (columns.isEmpty()) {
            emptyList()
        } else {
            columnNames(columns)
        }
    }

    /**
     * Normaliza fragmentos SQL libres que no deben estar vacios.
     */
    private fun sqlFragment(value: String, label: String): String {
        val fragment = value.trim()

        require(fragment.isNotEmpty()) {
            "$label no puede estar vacio."
        }

        return fragment
    }

    /**
     * Valida que un ENUM tenga valores no vacios y sin duplicados.
     */
    private fun enumValues(values: List<String>): List<String> {
        require(values.isNotEmpty()) {
            "Un ENUM debe tener al menos un valor."
        }

        val normalizedValues = values.map(::enumValue)

        require(normalizedValues.distinct().size == normalizedValues.size) {
            "Un ENUM no puede repetir valores."
        }

        return normalizedValues
    }

    /**
     * Normaliza un valor individual de ENUM.
     */
    private fun enumValue(value: String): String {
        val normalizedValue = value.trim()

        require(normalizedValue.isNotEmpty()) {
            "Un valor ENUM no puede estar vacio."
        }

        return normalizedValue
    }

    /**
     * Valida el timing soportado para un trigger.
     */
    private fun triggerTiming(value: String): String {
        val timing = value.trim().uppercase()

        require(timing in setOf("BEFORE", "AFTER", "INSTEAD OF")) {
            "Timing de trigger no soportado: $value."
        }

        return timing
    }

    /**
     * Valida uno o mas eventos soportados por `CREATE TRIGGER`.
     */
    private fun triggerEvents(values: List<String>): List<String> {
        require(values.isNotEmpty()) {
            "Debes indicar al menos un evento para el trigger."
        }

        return values.map { value ->
            val event = value.trim().uppercase()

            require(event in setOf("INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
                "Evento de trigger no soportado: $value."
            }

            event
        }
    }

    /**
     * Valida si el trigger corre por fila o por sentencia.
     */
    private fun triggerForEach(value: String): String {
        val forEach = value.trim().uppercase()

        require(forEach in setOf("ROW", "STATEMENT")) {
            "FOR EACH debe ser ROW o STATEMENT."
        }

        return forEach
    }

    /**
     * Normaliza la firma de funcion usada por un trigger.
     */
    private fun functionSignature(value: String): String {
        val signature = value.trim()

        require(signature.isNotEmpty()) {
            "La funcion del trigger no puede estar vacia."
        }

        return signature
    }
}
