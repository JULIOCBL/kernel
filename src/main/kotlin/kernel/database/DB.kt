package kernel.database

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class TransactionContext internal constructor(
    val connectionName: String?,
    val connection: Connection
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<TransactionContext>
}

class TransactionScope internal constructor(
    val connectionName: String?,
    val connection: Connection
)

class ConnectionFacade internal constructor(
    private val connectionName: String?
) {
    fun table(name: String): kernel.database.orm.QueryBuilder<Map<String, Any?>> {
        return kernel.database.orm.QueryBuilder(
            table = name,
            rowMapper = ::resultSetRowToMap,
            connectionName = connectionName
        )
    }
}

object DB {
    internal var connectionProviderOverride: ((String?) -> Connection)? = null
    internal var defaultConnectionNameOverride: (() -> String)? = null

    fun connection(name: String): ConnectionFacade {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) {
            "El nombre de la conexion no puede estar vacio."
        }

        return ConnectionFacade(normalized)
    }

    fun table(name: String): kernel.database.orm.QueryBuilder<Map<String, Any?>> {
        return ConnectionFacade(connectionName = null).table(name)
    }

    suspend fun <T> transaction(
        connectionName: String? = null,
        block: suspend TransactionScope.() -> T
    ): T {
        val resolvedConnectionName = resolveConnectionName(connectionName)
        val currentTransaction = currentCoroutineContext()[TransactionContext]
        if (currentTransaction != null) {
            require(resolvedConnectionName == null || resolvedConnectionName == currentTransaction.connectionName) {
                "Ya existe una transacción activa para `${currentTransaction.connectionName ?: "default"}` y no puede mezclarse con `${connectionName ?: "default"}`."
            }

            return TransactionScope(
                connectionName = currentTransaction.connectionName,
                connection = currentTransaction.connection
            ).block()
        }

        val connection = resolveConnection(resolvedConnectionName)
        val previousAutoCommit = connection.autoCommit
        connection.autoCommit = false

        return try {
            withContext(TransactionContext(resolvedConnectionName, connection)) {
                val result = TransactionScope(resolvedConnectionName, connection).block()
                connection.commit()
                result
            }
        } catch (error: Throwable) {
            runCatching { connection.rollback() }
            throw error
        } finally {
            runCatching { connection.autoCommit = previousAutoCommit }
            runCatching { connection.close() }
        }
    }

    suspend fun <T> withConnection(
        connectionName: String? = null,
        block: (Connection) -> T
    ): T {
        val resolvedConnectionName = resolveConnectionName(connectionName)
        val currentTransaction = currentCoroutineContext()[TransactionContext]
        if (currentTransaction != null &&
            (resolvedConnectionName == null || resolvedConnectionName == currentTransaction.connectionName)
        ) {
            return block(currentTransaction.connection)
        }

        val connection = resolveConnection(resolvedConnectionName)
        return try {
            block(connection)
        } finally {
            runCatching { connection.close() }
        }
    }

    private fun resolveConnection(connectionName: String?): Connection {
        return connectionProviderOverride?.invoke(connectionName)
            ?: databaseManager().connect(connectionName)
    }

    private fun resolveConnectionName(connectionName: String?): String? {
        val normalized = connectionName?.trim()?.takeUnless(String::isEmpty)
        if (normalized != null) {
            return normalized
        }

        if (connectionProviderOverride != null) {
            return defaultConnectionNameOverride?.invoke()
        }

        return defaultConnectionNameOverride?.invoke()
            ?: runCatching { databaseManager().defaultConnectionName() }.getOrNull()
    }
}

private fun resultSetRowToMap(resultSet: ResultSet): Map<String, Any?> {
    val metadata = resultSet.metaData
    return buildMap {
        for (index in 1..metadata.columnCount) {
            val label = columnLabel(metadata, index)
            put(label, resultSet.getObject(index))
        }
    }
}

private fun columnLabel(
    metadata: ResultSetMetaData,
    index: Int
): String {
    return metadata.getColumnLabel(index)
        ?.takeIf(String::isNotBlank)
        ?: metadata.getColumnName(index)
}
