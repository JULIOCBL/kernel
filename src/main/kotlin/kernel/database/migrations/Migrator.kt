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
            val selectedNames = normalizedNames(options.only)
            validateRequestedMigrations(selectedNames)

            val pending = pendingDefinitions(selectedNames)
            if (pending.isEmpty()) {
                return@usingConnection emptyList()
            }

            val batchNumbers = mutableMapOf<String, Int>()
            pending.forEach { resolved ->
                ensureRepositoryExists(resolved.connection)
                val batch = batchNumbers.getOrPut(resolved.connection) {
                    getNextBatchNumber(resolved.connection)
                }
                runUp(resolved, batch)
            }

            pending.map { resolved -> resolved.definition.name }
        }
    }

    fun rollback(options: MigrationRollbackOptions = MigrationRollbackOptions()): List<String> {
        return usingConnection(options.database) {
            val migrations = rollbackCandidates(options.steps)

            if (migrations.isEmpty()) {
                return@usingConnection emptyList()
            }

            migrations.forEach(::runDown)
            migrations.map { candidate -> candidate.record.migration }
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

            val batchesByConnection = mutableMapOf<String, Map<String, Int>>()

            definitions.map { definition ->
                val migration = definition.create()
                val connection = resolveMigrationConnectionName(migration)
                val batches = batchesByConnection.getOrPut(connection) {
                    getMigrationBatches(connection)
                }
                val batch = batches[definition.name]

                MigrationStatus(
                    migration = definition.name,
                    status = if (batch != null) MigrationState.RAN else MigrationState.PENDING,
                    batch = batch,
                    connection = connection
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

        return pendingDefinitions(selectedNames).map(ResolvedMigration::definition)
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

    private fun ensureRepositoryExists(connection: String) {
        if (!repositoryExists(connection)) {
            usingRepositorySource(connection) {
                repository.createRepository()
            }
        }
    }

    private fun runUp(resolved: ResolvedMigration, batch: Int) {
        runMigration(resolved.migration, resolved.connection, direction = MigrationDirection.UP)
        usingRepositorySource(resolved.connection) {
            repository.log(resolved.definition.name, batch)
        }
    }

    private fun runDown(candidate: ScopedMigrationRecord) {
        val definition = registry.find(candidate.record.migration)
            ?: throw IllegalStateException(
                "La migracion `${candidate.record.migration}` no existe en el MigrationRegistry."
            )

        val migration = definition.create()
        val connection = resolveMigrationConnectionName(migration)

        require(connection == candidate.connection) {
            "La migracion `${candidate.record.migration}` esperaba la conexion `$connection`, " +
                "pero fue recuperada desde `${candidate.connection}`."
        }

        runMigration(migration, candidate.connection, direction = MigrationDirection.DOWN)
        usingRepositorySource(candidate.connection) {
            repository.delete(candidate.record)
        }
    }

    private fun runMigration(
        migration: Migration,
        targetConnection: String,
        direction: MigrationDirection
    ) {
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

    private fun pendingDefinitions(selectedNames: Set<String>): List<ResolvedMigration> {
        val ranByConnection = mutableMapOf<String, Set<String>>()

        return registry.all()
            .asSequence()
            .filter { definition ->
                selectedNames.isEmpty() || definition.name in selectedNames
            }
            .map { definition ->
                val migration = definition.create()
                ResolvedMigration(
                    definition = definition,
                    migration = migration,
                    connection = resolveMigrationConnectionName(migration)
                )
            }
            .filter { resolved ->
                val ran = ranByConnection.getOrPut(resolved.connection) {
                    getRan(resolved.connection).toSet()
                }
                resolved.definition.name !in ran
            }
            .toList()
    }

    private fun rollbackCandidates(steps: Int): List<ScopedMigrationRecord> {
        val connections = registry.all()
            .asSequence()
            .map { definition ->
                resolveMigrationConnectionName(definition.create())
            }
            .distinct()
            .toList()

        val candidates = connections.flatMap { connection ->
            val records = if (!repositoryExists(connection)) {
                emptyList()
            } else if (steps > 0) {
                usingRepositorySource(connection) {
                    repository.getMigrations(steps)
                }
            } else {
                usingRepositorySource(connection) {
                    repository.getLast()
                }
            }

            records.map { record ->
                ScopedMigrationRecord(record = record, connection = connection)
            }
        }.sortedWith(
            compareByDescending<ScopedMigrationRecord> { candidate -> candidate.record.batch }
                .thenByDescending { candidate -> candidate.record.migration }
                .thenBy { candidate -> candidate.connection }
        )

        return if (steps > 0) {
            candidates.take(steps)
        } else {
            candidates
        }
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

    private fun repositoryExists(connection: String): Boolean {
        return usingRepositorySource(connection) {
            repository.repositoryExists()
        }
    }

    private fun getRan(connection: String): List<String> {
        return if (repositoryExists(connection)) {
            usingRepositorySource(connection) { repository.getRan() }
        } else {
            emptyList()
        }
    }

    private fun getMigrationBatches(connection: String): Map<String, Int> {
        return if (repositoryExists(connection)) {
            usingRepositorySource(connection) { repository.getMigrationBatches() }
        } else {
            emptyMap()
        }
    }

    private fun getNextBatchNumber(connection: String): Int {
        return usingRepositorySource(connection) {
            repository.getNextBatchNumber()
        }
    }

    private fun <T> usingRepositorySource(name: String, block: () -> T): T {
        val previousSource = repositorySource()
        repository.setSource(name)

        return try {
            block()
        } finally {
            repository.setSource(previousSource)
        }
    }

    private fun repositorySource(): String? {
        return normalizeConnectionName(connectionName)
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
            statements.forEach { sql ->
                try {
                    statement.execute(sql)
                } catch (error: Throwable) {
                    throw IllegalStateException(
                        "SQL error while executing migration statement: $sql",
                        error
                    )
                }
            }
        }
    }

    private enum class MigrationDirection {
        UP,
        DOWN
    }

    private data class ResolvedMigration(
        val definition: MigrationDefinition,
        val migration: Migration,
        val connection: String
    )

    private data class ScopedMigrationRecord(
        val record: MigrationRecord,
        val connection: String
    )
}
