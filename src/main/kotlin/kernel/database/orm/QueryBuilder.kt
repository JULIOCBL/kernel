package kernel.database.orm

import kernel.database.DB
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

private sealed interface WhereNode {
    val boolean: String
}

private data class WhereClause(
    override val boolean: String,
    val kind: WhereKind,
    val column: String,
    val operator: String,
    val values: List<Any?> = emptyList()
) : WhereNode

private data class WhereGroup(
    override val boolean: String,
    val nodes: List<WhereNode>
) : WhereNode

private enum class WhereKind {
    BASIC,
    IN,
    NOT_IN,
    NULL,
    NOT_NULL
}

private data class JoinClause(
    val type: String,
    val table: String,
    val left: String,
    val operator: String,
    val right: String
)

private data class OrderByClause(
    val column: String,
    val direction: String
)

class QueryBuilder<T>(
    private val table: String,
    private val rowMapper: (ResultSet) -> T,
    private val connectionName: String? = null
) {
    private val selectedColumns = mutableListOf<String>()
    private val whereNodes = mutableListOf<WhereNode>()
    private val joins = mutableListOf<JoinClause>()
    private val orderByClauses = mutableListOf<OrderByClause>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null

    private val normalizedTable = sanitizeIdentifier(table)

    fun select(vararg columns: String): QueryBuilder<T> {
        selectedColumns.clear()
        selectedColumns.addAll(
            columns
                .map(String::trim)
                .filter(String::isNotBlank)
                .map(::sanitizeProjection)
        )
        return this
    }

    fun where(column: String, operator: String, value: Any?): QueryBuilder<T> {
        return addBasicWhere("AND", column, operator, value)
    }

    fun where(group: QueryBuilder<T>.() -> Unit): QueryBuilder<T> {
        return addWhereGroup("AND", group)
    }

    fun orWhere(column: String, operator: String, value: Any?): QueryBuilder<T> {
        return addBasicWhere("OR", column, operator, value)
    }

    fun orWhere(group: QueryBuilder<T>.() -> Unit): QueryBuilder<T> {
        return addWhereGroup("OR", group)
    }

    fun whereIn(column: String, values: Iterable<Any?>): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "AND",
            kind = WhereKind.IN,
            column = sanitizeIdentifier(column),
            operator = "IN",
            values = normalizeWhereValues(values)
        )
        return this
    }

    fun orWhereIn(column: String, values: Iterable<Any?>): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "OR",
            kind = WhereKind.IN,
            column = sanitizeIdentifier(column),
            operator = "IN",
            values = normalizeWhereValues(values)
        )
        return this
    }

    fun whereNotIn(column: String, values: Iterable<Any?>): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "AND",
            kind = WhereKind.NOT_IN,
            column = sanitizeIdentifier(column),
            operator = "NOT IN",
            values = normalizeWhereValues(values)
        )
        return this
    }

    fun orWhereNotIn(column: String, values: Iterable<Any?>): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "OR",
            kind = WhereKind.NOT_IN,
            column = sanitizeIdentifier(column),
            operator = "NOT IN",
            values = normalizeWhereValues(values)
        )
        return this
    }

    fun whereNull(column: String): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "AND",
            kind = WhereKind.NULL,
            column = sanitizeIdentifier(column),
            operator = "IS NULL"
        )
        return this
    }

    fun orWhereNull(column: String): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "OR",
            kind = WhereKind.NULL,
            column = sanitizeIdentifier(column),
            operator = "IS NULL"
        )
        return this
    }

    fun whereNotNull(column: String): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "AND",
            kind = WhereKind.NOT_NULL,
            column = sanitizeIdentifier(column),
            operator = "IS NOT NULL"
        )
        return this
    }

    fun orWhereNotNull(column: String): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = "OR",
            kind = WhereKind.NOT_NULL,
            column = sanitizeIdentifier(column),
            operator = "IS NOT NULL"
        )
        return this
    }

    fun join(table: String, left: String, operator: String, right: String): QueryBuilder<T> {
        joins += JoinClause(
            type = "JOIN",
            table = sanitizeIdentifier(table),
            left = sanitizeIdentifier(left),
            operator = sanitizeOperator(operator),
            right = sanitizeIdentifier(right)
        )
        return this
    }

    fun leftJoin(table: String, left: String, operator: String, right: String): QueryBuilder<T> {
        joins += JoinClause(
            type = "LEFT JOIN",
            table = sanitizeIdentifier(table),
            left = sanitizeIdentifier(left),
            operator = sanitizeOperator(operator),
            right = sanitizeIdentifier(right)
        )
        return this
    }

    fun orderBy(column: String, direction: String = "asc"): QueryBuilder<T> {
        orderByClauses += OrderByClause(
            column = sanitizeIdentifier(column),
            direction = sanitizeDirection(direction)
        )
        return this
    }

    fun orderByDesc(column: String): QueryBuilder<T> = orderBy(column, "desc")

    fun latest(column: String = "created_at"): QueryBuilder<T> = orderBy(column, "desc")

    fun oldest(column: String = "created_at"): QueryBuilder<T> = orderBy(column, "asc")

    fun reorder(): QueryBuilder<T> {
        orderByClauses.clear()
        return this
    }

    fun reorder(column: String, direction: String = "asc"): QueryBuilder<T> {
        orderByClauses.clear()
        return orderBy(column, direction)
    }

    fun limit(value: Int): QueryBuilder<T> {
        require(value >= 0) {
            "El limite no puede ser negativo."
        }

        limitValue = value
        return this
    }

    fun offset(value: Int): QueryBuilder<T> {
        require(value >= 0) {
            "El offset no puede ser negativo."
        }

        offsetValue = value
        return this
    }

    suspend fun get(): List<T> {
        return DB.withConnection(connectionName) { connection ->
            connection.prepareStatement(buildSelectSql()).use { statement ->
                bindWhereValues(statement, offset = 1)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(rowMapper(resultSet))
                        }
                    }
                }
            }
        }
    }

    suspend fun first(): T? = get().firstOrNull()

    suspend fun insert(values: Map<String, Any?>): Int {
        require(values.isNotEmpty()) {
            "No se puede insertar un registro vacío en `$normalizedTable`."
        }

        val normalizedValues = normalizeAssignmentValues(values)
        val columns = normalizedValues.keys.joinToString(", ")
        val placeholders = normalizedValues.keys.joinToString(", ") { "?" }
        val sql = "INSERT INTO $normalizedTable ($columns) VALUES ($placeholders)"

        return DB.withConnection(connectionName) { connection ->
            connection.prepareStatement(sql).use { statement ->
                bindValues(statement, normalizedValues.values.toList(), offset = 1)
                statement.executeUpdate()
            }
        }
    }

    suspend fun update(values: Map<String, Any?>): Int {
        require(values.isNotEmpty()) {
            "No se puede actualizar un registro sin columnas para `$normalizedTable`."
        }
        require(whereNodes.isNotEmpty()) {
            "Por seguridad, `update()` requiere al menos una clausula `where` en `$normalizedTable`."
        }

        val normalizedValues = normalizeAssignmentValues(values)
        val assignments = normalizedValues.keys.joinToString(", ") { column -> "$column = ?" }
        val sql = buildString {
            append("UPDATE $normalizedTable SET $assignments")
            append(buildWhereSql())
        }

        return DB.withConnection(connectionName) { connection ->
            connection.prepareStatement(sql).use { statement ->
                bindValues(statement, normalizedValues.values.toList(), offset = 1)
                bindWhereValues(statement, offset = normalizedValues.size + 1)
                statement.executeUpdate()
            }
        }
    }

    suspend fun upsert(
        records: List<Map<String, Any?>>,
        uniqueBy: List<String>,
        updateColumns: List<String> = emptyList()
    ): Int {
        require(records.isNotEmpty()) {
            "No se puede hacer upsert sin registros en `$normalizedTable`."
        }

        val normalizedColumns = normalizeUpsertColumns(records)
        val normalizedUniqueBy = uniqueBy.map(::sanitizeIdentifier).distinct()
        require(normalizedUniqueBy.isNotEmpty()) {
            "Debes indicar al menos una columna unica para `upsert()` en `$normalizedTable`."
        }
        require(normalizedUniqueBy.all { it in normalizedColumns }) {
            "Las columnas unicas de `upsert()` deben existir en los registros para `$normalizedTable`."
        }

        val normalizedUpdateColumns = if (updateColumns.isEmpty()) {
            normalizedColumns.filterNot { it in normalizedUniqueBy }
        } else {
            updateColumns.map(::sanitizeIdentifier).distinct().also { requested ->
                require(requested.all { it in normalizedColumns }) {
                    "Las columnas a actualizar en `upsert()` deben existir en los registros para `$normalizedTable`."
                }
            }
        }

        val normalizedRecords = records.map { record ->
            val sanitized = record.entries.associate { (column, value) ->
                sanitizeIdentifier(column) to value
            }

            normalizedColumns.associateWith { column -> sanitized[column] }
        }

        return DB.withConnection(connectionName) { connection ->
            val driverId = resolveDriverId(connection)
            val sql = buildUpsertSql(
                driverId = driverId,
                columns = normalizedColumns,
                uniqueBy = normalizedUniqueBy,
                updateColumns = normalizedUpdateColumns,
                recordCount = normalizedRecords.size
            )

            connection.prepareStatement(sql).use { statement ->
                val values = normalizedRecords.flatMap { record ->
                    normalizedColumns.map { column -> record[column] }
                }
                bindValues(statement, values, offset = 1)
                statement.executeUpdate()
            }
        }
    }

    internal fun buildSelectSql(): String {
        val projection = if (selectedColumns.isEmpty()) "*" else selectedColumns.joinToString(", ")
        val joinSql = joins.joinToString(separator = " ") { join ->
            "${join.type} ${join.table} ON ${join.left} ${join.operator} ${join.right}"
        }.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty()
        val orderBySql = orderByClauses.takeIf { it.isNotEmpty() }?.joinToString(
            prefix = " ORDER BY ",
            separator = ", "
        ) { clause ->
            "${clause.column} ${clause.direction}"
        }.orEmpty()
        val limitSql = limitValue?.let { " LIMIT $it" }.orEmpty()
        val offsetSql = offsetValue?.let { " OFFSET $it" }.orEmpty()

        return buildString {
            append("SELECT $projection FROM $normalizedTable")
            append(joinSql)
            append(buildWhereSql())
            append(orderBySql)
            append(limitSql)
            append(offsetSql)
        }
    }

    private fun buildWhereSql(): String {
        if (whereNodes.isEmpty()) {
            return ""
        }

        return " WHERE ${compileWhereNodes(whereNodes)}"
    }

    private fun compileWhereNodes(nodes: List<WhereNode>): String {
        return nodes.mapIndexed { index, node ->
            val compiled = when (node) {
                is WhereClause -> compileWhereClause(node)
                is WhereGroup -> "(${compileWhereNodes(node.nodes)})"
            }

            if (index == 0) {
                compiled
            } else {
                "${node.boolean} $compiled"
            }
        }.joinToString(" ")
    }

    private fun bindWhereValues(statement: PreparedStatement, offset: Int) {
        val orderedValues = flattenWhereValues(whereNodes)
        bindValues(statement, orderedValues, offset)
    }

    private fun flattenWhereValues(nodes: List<WhereNode>): List<Any?> {
        return nodes.flatMap { node ->
            when (node) {
                is WhereClause -> when (node.kind) {
                    WhereKind.BASIC,
                    WhereKind.IN,
                    WhereKind.NOT_IN -> node.values
                    WhereKind.NULL,
                    WhereKind.NOT_NULL -> emptyList()
                }
                is WhereGroup -> flattenWhereValues(node.nodes)
            }
        }
    }

    private fun bindValues(statement: PreparedStatement, values: List<Any?>, offset: Int) {
        values.forEachIndexed { index, value ->
            statement.setObject(offset + index, value)
        }
    }

    private fun normalizeAssignmentValues(values: Map<String, Any?>): Map<String, Any?> {
        return values.entries
            .associate { (column, value) ->
                sanitizeIdentifier(column) to value
            }
            .toSortedMap()
    }

    private fun normalizeUpsertColumns(records: List<Map<String, Any?>>): List<String> {
        val columns = records
            .flatMap { record -> record.keys }
            .map(::sanitizeIdentifier)
            .distinct()
            .sorted()

        require(columns.isNotEmpty()) {
            "No se puede hacer upsert con registros vacios en `$normalizedTable`."
        }

        return columns
    }

    private fun normalizeWhereValues(values: Iterable<Any?>): List<Any?> {
        val normalized = values.toList()
        require(normalized.isNotEmpty()) {
            "La clausula requiere al menos un valor."
        }

        return normalized
    }

    private fun compileWhereClause(clause: WhereClause): String {
        return when (clause.kind) {
            WhereKind.BASIC -> "${clause.column} ${clause.operator} ?"
            WhereKind.IN -> {
                val placeholders = clause.values.joinToString(", ") { "?" }
                "${clause.column} IN ($placeholders)"
            }
            WhereKind.NOT_IN -> {
                val placeholders = clause.values.joinToString(", ") { "?" }
                "${clause.column} NOT IN ($placeholders)"
            }
            WhereKind.NULL -> "${clause.column} IS NULL"
            WhereKind.NOT_NULL -> "${clause.column} IS NOT NULL"
        }
    }

    private fun addBasicWhere(
        boolean: String,
        column: String,
        operator: String,
        value: Any?
    ): QueryBuilder<T> {
        whereNodes += WhereClause(
            boolean = boolean,
            kind = WhereKind.BASIC,
            column = sanitizeIdentifier(column),
            operator = sanitizeOperator(operator),
            values = listOf(value)
        )
        return this
    }

    private fun addWhereGroup(
        boolean: String,
        block: QueryBuilder<T>.() -> Unit
    ): QueryBuilder<T> {
        val nestedBuilder = QueryBuilder(
            table = normalizedTable,
            rowMapper = rowMapper,
            connectionName = connectionName
        )
        nestedBuilder.block()

        if (nestedBuilder.whereNodes.isNotEmpty()) {
            whereNodes += WhereGroup(
                boolean = boolean,
                nodes = nestedBuilder.whereNodes.toList()
            )
        }

        return this
    }

    private fun sanitizeProjection(value: String): String {
        val trimmed = value.trim()
        if (trimmed == "*") {
            return trimmed
        }

        if (trimmed.endsWith(".*")) {
            val prefix = trimmed.removeSuffix(".*")
            return "${sanitizeIdentifier(prefix)}.*"
        }

        return sanitizeIdentifier(trimmed)
    }

    private fun sanitizeIdentifier(value: String): String {
        val normalized = value.trim()
        require(normalized.isNotBlank()) {
            "El identificador SQL no puede estar vacío."
        }
        require(IDENTIFIER_PATTERN.matches(normalized)) {
            "Identificador SQL inválido: `$value`."
        }

        return normalized
    }

    private fun sanitizeOperator(value: String): String {
        val normalized = value.trim().uppercase()
        require(normalized in ALLOWED_OPERATORS) {
            "Operador SQL no permitido: `$value`."
        }

        return normalized
    }

    private fun sanitizeDirection(value: String): String {
        val normalized = value.trim().uppercase()
        require(normalized in setOf("ASC", "DESC")) {
            "Dirección de orden inválida: `$value`."
        }

        return normalized
    }

    private fun resolveDriverId(connection: Connection): String {
        val productName = connection.metaData.databaseProductName.orEmpty().lowercase()

        return when {
            "postgres" in productName -> "pgsql"
            "mariadb" in productName || "mysql" in productName -> "mariadb"
            else -> error("No existe soporte de `upsert()` para el motor `$productName`.")
        }
    }

    internal fun buildUpsertSql(
        driverId: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateColumns: List<String>,
        recordCount: Int
    ): String {
        require(recordCount > 0) {
            "Debes indicar al menos un registro para construir un `upsert()` en `$normalizedTable`."
        }

        val normalizedColumns = columns.map(::sanitizeIdentifier).distinct()
        val normalizedUniqueBy = uniqueBy.map(::sanitizeIdentifier).distinct()
        val normalizedUpdateColumns = updateColumns.map(::sanitizeIdentifier).distinct()
        val placeholders = normalizedColumns.joinToString(", ") { "?" }
        val valuesSql = List(recordCount) { "($placeholders)" }.joinToString(", ")
        val columnsSql = normalizedColumns.joinToString(", ")

        return when (driverId.lowercase()) {
            "pgsql" -> {
                val conflictSql = normalizedUniqueBy.joinToString(", ")
                val actionSql = if (normalizedUpdateColumns.isEmpty()) {
                    "DO NOTHING"
                } else {
                    val assignments = normalizedUpdateColumns.joinToString(", ") { column ->
                        "$column = EXCLUDED.$column"
                    }
                    "DO UPDATE SET $assignments"
                }

                "INSERT INTO $normalizedTable ($columnsSql) VALUES $valuesSql ON CONFLICT ($conflictSql) $actionSql"
            }
            "mariadb" -> {
                val assignments = if (normalizedUpdateColumns.isEmpty()) {
                    val keepColumn = normalizedUniqueBy.first()
                    "$keepColumn = $keepColumn"
                } else {
                    normalizedUpdateColumns.joinToString(", ") { column ->
                        "$column = VALUES($column)"
                    }
                }

                "INSERT INTO $normalizedTable ($columnsSql) VALUES $valuesSql ON DUPLICATE KEY UPDATE $assignments"
            }
            else -> error("No existe soporte de `upsert()` para el driver `$driverId`.")
        }
    }

    companion object {
        private val IDENTIFIER_PATTERN = Regex(
            "^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$"
        )
        private val ALLOWED_OPERATORS = setOf(
            "=",
            "!=",
            "<>",
            ">",
            ">=",
            "<",
            "<=",
            "LIKE"
        )
    }
}
