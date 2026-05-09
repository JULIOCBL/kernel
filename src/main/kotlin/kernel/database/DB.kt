package kernel.database

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.sql.Connection
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

object DB {
    internal var connectionProviderOverride: ((String?) -> Connection)? = null
    internal var defaultConnectionNameOverride: (() -> String)? = null

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
