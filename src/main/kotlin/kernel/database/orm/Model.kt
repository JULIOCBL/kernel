package kernel.database.orm

import java.sql.ResultSet

abstract class Model {
    open val tableName: String
        get() = inferTableName(javaClass.simpleName)
    open val connectionName: String? = null
    open val primaryKey: String = "id"
    private var exists: Boolean = false

    protected abstract fun primaryKeyValue(): Any?
    protected abstract fun persistenceAttributes(): Map<String, Any?>

    suspend fun save(): Int {
        val attributes = persistenceAttributes()
        val identifier = primaryKeyValue()
        val builder = QueryBuilder<Unit>(
            table = tableName,
            rowMapper = { Unit },
            connectionName = connectionName
        )

        val affectedRows = if (!exists) {
            builder.insert(attributes)
        } else {
            builder
                .where(primaryKey, "=", identifier)
                .update(attributes - primaryKey)
        }

        if (affectedRows > 0) {
            exists = true
        }

        return affectedRows
    }

    protected fun <M : Model> persisted(model: M): M {
        model.exists = true
        return model
    }
}

abstract class ModelDefinition<M : Model>(
    private val tableName: String? = null,
    private val mapper: (ResultSet) -> M,
    private val connectionName: String? = null,
    private val primaryKey: String = "id"
) {
    fun query(): QueryBuilder<M> {
        return QueryBuilder(
            table = tableName ?: inferTableName(inferModelSimpleName()),
            rowMapper = mapper,
            connectionName = connectionName
        )
    }

    fun where(column: String, operator: String, value: Any?): QueryBuilder<M> {
        return query().where(column, operator, value)
    }

    suspend fun all(): List<M> = query().get()

    open suspend fun find(id: Any?): M? {
        return query()
            .where(primaryKey, "=", id)
            .first()
    }

    private fun inferModelSimpleName(): String {
        return javaClass.enclosingClass?.simpleName
            ?: error(
                "No se pudo inferir el nombre del modelo para `${javaClass.name}`. " +
                    "Define `tableName` explicitamente."
            )
    }
}

internal fun inferTableName(modelSimpleName: String): String {
    val normalizedName = modelSimpleName.trim()
    require(normalizedName.isNotEmpty()) {
        "El nombre del modelo no puede estar vacio."
    }

    val snakeCase = normalizedName
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .lowercase()

    return when {
        snakeCase.endsWith("y") && snakeCase.length > 1 && snakeCase[snakeCase.length - 2] !in "aeiou" ->
            snakeCase.dropLast(1) + "ies"
        snakeCase.endsWith("s")
            || snakeCase.endsWith("x")
            || snakeCase.endsWith("z")
            || snakeCase.endsWith("ch")
            || snakeCase.endsWith("sh") -> snakeCase + "es"
        else -> snakeCase + "s"
    }
}
