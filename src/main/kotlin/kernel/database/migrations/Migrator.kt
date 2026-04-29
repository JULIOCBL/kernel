package kernel.database.migrations

import kernel.database.pdo.connections.ConnectionResolver
import java.sql.Connection

data class MigrationRunOptions(
    val database: String? = null,
    val only: Set<String> = emptySet()
)

data class MigrationRollbackOptions(
    val database: String? = null,
    val steps: Int = 0
)

data class MigrationStatusOptions(
    val database: String? = null,
    val only: Set<String> = emptySet()
)

/**
 * Ejecuta migraciones registradas explicitamente contra conexiones nombradas.
 *
 * A diferencia de Laravel, hoy el kernel no descubre clases desde archivos
 * `.php`; por eso opera sobre un `MigrationRegistry` explicito.
 */
class Migrator(
    private val repository: MigrationRepository,
    private val resolver: ConnectionResolver,
    private val registry: MigrationRegistry,
    private val sqlGenerator: MigrationSqlGenerator = MigrationSqlGenerator()
) {
    private var connectionName: String? = null

    fun run(options: MigrationRunOptions = MigrationRunOptions()): List<String> {
        return usingConnection(options.database) {
            val pending = pendingMigrations(options.only)
            if (pending.isEmpty()) {
                return@usingConnection emptyList()
            }

            ensureRepositoryExists()
            val batch = repository.getNextBatchNumber()
            pending.forEach { definition ->
                runUp(definition, batch)
            }

            pending.map(MigrationDefinition::name)
        }
    }

    fun rollback(options: MigrationRollbackOptions = MigrationRollbackOptions()): List<String> {
        return usingConnection(options.database) {
            ensureRepositoryExists()

            val migrations = if (options.steps > 0) {
                repository.getMigrations(options.steps)
            } else {
                repository.getLast()
            }

            if (migrations.isEmpty()) {
                return@usingConnection emptyList()
            }

            migrations.forEach(::runDown)
            migrations.map(MigrationRecord::migration)
        }
    }

    fun status(options: MigrationStatusOptions = MigrationStatusOptions()): List<MigrationStatus> {
        return usingConnection(options.database) {
            val selectedNames = normalizedNames(options.only)
            validateRequestedMigrations(selectedNames)

            val definitions = registry.all()
                .asSequence()
                .filter { definition ->
                    selectedNames.isEmpty() || definition.name in selectedNames
                }
                .toList()

            if (definitions.isEmpty()) {
                return@usingConnection emptyList()
            }

            val batches = if (repository.repositoryExists()) {
                repository.getMigrationBatches()
            } else {
                emptyMap()
            }

            definitions.map { definition ->
                val migration = definition.create()
                val batch = batches[definition.name]

                MigrationStatus(
                    migration = definition.name,
                    status = if (batch != null) MigrationState.RAN else MigrationState.PENDING,
                    batch = batch,
                    connection = resolveMigrationConnectionName(migration)
                )
            }
        }
    }

    fun pendingMigrations(only: Set<String> = emptySet()): List<MigrationDefinition> {
        val selectedNames = normalizedNames(only)
        validateRequestedMigrations(selectedNames)

        if (registry.isEmpty()) {
            return emptyList()
        }

        val ran = if (repository.repositoryExists()) repository.getRan().toSet() else emptySet()

        return registry.all()
            .asSequence()
            .filter { definition ->
                selectedNames.isEmpty() || definition.name in selectedNames
            }
            .filterNot { definition -> definition.name in ran }
            .toList()
    }

    fun setConnection(name: String?): Migrator {
        connectionName = normalizeConnectionName(name)
        repository.setSource(connectionName)
        return this
    }

    fun getConnection(): String? = connectionName

    fun <T> usingConnection(name: String?, block: () -> T): T {
        val previous = connectionName
        setConnection(name)

        return try {
            block()
        } finally {
            setConnection(previous)
        }
    }

    private fun ensureRepositoryExists() {
        if (!repository.repositoryExists()) {
            repository.createRepository()
        }
    }

    private fun runUp(definition: MigrationDefinition, batch: Int) {
        val migration = definition.create()
        runMigration(migration, direction = MigrationDirection.UP)
        repository.log(definition.name, batch)
    }

    private fun runDown(record: MigrationRecord) {
        val definition = registry.find(record.migration)
            ?: throw IllegalStateException(
                "La migracion `${record.migration}` no existe en el MigrationRegistry."
            )

        runMigration(definition.create(), direction = MigrationDirection.DOWN)
        repository.delete(record)
    }

    private fun runMigration(
        migration: Migration,
        direction: MigrationDirection
    ) {
        val targetConnection = resolveMigrationConnectionName(migration)
        val connectionConfig = resolver.connectionConfig(targetConnection)
        connectionConfig.requireSchemaMigrationSupport()
        val statements = when (direction) {
            MigrationDirection.UP -> sqlGenerator.generateUpStatements(migration, connectionConfig.driver)
            MigrationDirection.DOWN -> sqlGenerator.generateDownStatements(migration, connectionConfig.driver)
        }

        if (statements.isEmpty()) {
            return
        }

        resolver.connection(targetConnection).use { connection ->
            if (migration.withinTransaction && connectionConfig.supportsSchemaTransactions()) {
                connection.inTransaction(statements)
            } else {
                connection.executeStatements(statements)
            }
        }
    }

    private fun resolveMigrationConnectionName(migration: Migration): String {
        return normalizeConnectionName(migration.connectionName)
            ?: connectionName
            ?: resolver.defaultConnectionName()
    }

    private fun validateRequestedMigrations(requestedNames: Set<String>) {
        val missing = requestedNames.filterNot(registry::contains)
        require(missing.isEmpty()) {
            "Las migraciones solicitadas no existen en el registry: ${missing.joinToString(", ")}."
        }
    }

    private fun normalizedNames(names: Set<String>): Set<String> {
        return names.mapNotNull { value ->
            value.trim().takeIf(String::isNotEmpty)
        }.toSet()
    }

    private fun normalizeConnectionName(name: String?): String? {
        return name?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun Connection.inTransaction(statements: List<String>) {
        val previousAutoCommit = autoCommit

        try {
            autoCommit = false
            executeStatements(statements)
            commit()
        } catch (error: Throwable) {
            rollback()
            throw error
        } finally {
            autoCommit = previousAutoCommit
        }
    }

    private fun Connection.executeStatements(statements: List<String>) {
        createStatement().use { statement ->
            statements.forEach(statement::execute)
        }
    }

    private enum class MigrationDirection {
        UP,
        DOWN
    }
}
