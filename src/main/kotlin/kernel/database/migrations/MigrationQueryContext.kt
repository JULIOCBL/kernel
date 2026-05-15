package kernel.database.migrations

import kernel.database.collector.MigrationCollector
import kernel.database.statements.RawSqlStatement

internal object MigrationQueryContext {
    private val current = ThreadLocal<ActiveContext?>()

    data class ActiveContext(
        val collector: MigrationCollector,
        val connectionName: String?
    )

    fun current(): ActiveContext? = current.get()

    fun <T> withContext(
        collector: MigrationCollector,
        connectionName: String?,
        block: () -> T
    ): T {
        val previous = current.get()
        current.set(
            ActiveContext(
                collector = collector,
                connectionName = connectionName?.trim()?.takeUnless(String::isEmpty)
            )
        )

        return try {
            block()
        } finally {
            current.set(previous)
        }
    }

    fun recordStatement(
        sql: String,
        requestedConnectionName: String?
    ) {
        val context = current()
            ?: error("No existe un contexto activo de migracion para registrar la sentencia.")

        val normalizedRequested = requestedConnectionName?.trim()?.takeUnless(String::isEmpty)
        val normalizedMigration = context.connectionName

        if (normalizedRequested != null && normalizedMigration == null) {
            error(
                "DB.connection(\"$normalizedRequested\") dentro de migraciones requiere " +
                    "que la migracion declare `override val connectionName = \"$normalizedRequested\"`."
            )
        }

        if (normalizedRequested != null && normalizedMigration != null && normalizedRequested != normalizedMigration) {
            error(
                "La migracion esta asociada a la conexion `$normalizedMigration`, " +
                    "pero la operacion intento usar `$normalizedRequested`."
            )
        }

        context.collector.add(RawSqlStatement(sql))
    }
}
